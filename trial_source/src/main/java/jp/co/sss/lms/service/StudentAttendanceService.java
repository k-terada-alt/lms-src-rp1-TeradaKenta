package jp.co.sss.lms.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @author 寺田健大 -Task26
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
		attendanceForm.setHourMap(attendanceUtil.getHourMap());
		attendanceForm.setMinuteMap(attendanceUtil.getMinuteMap());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			//寺田健大 -Task26
			//出勤時間を取得
			String startStr = dailyAttendanceForm.getTrainingStartTime();
			
			//勤怠Utilを使用し時間を「時間」と「分」に分割する
			dailyAttendanceForm.setStartTimeHour(attendanceUtil.getHourFromString(startStr));
			dailyAttendanceForm.setStartTimeMinute(attendanceUtil.getMinuteFromString(startStr));

			//寺田健大 -Task26
			//退勤時間を取得
			String endStr = dailyAttendanceForm.getTrainingEndTime();

			//勤怠Utilを使用し時間を「時間」と「分」に分割する
			dailyAttendanceForm.setEndTimeHour(attendanceUtil.getHourFromString(endStr));
			dailyAttendanceForm.setEndTimeMinute(attendanceUtil.getMinuteFromString(endStr));

			//寺田健大 -Task26
			//分割した時間をリストに入れなおす
			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 過去日の未入力チェック
	 * 概要：今日より前の過去日に、未入力の勤怠があるかどうかを判定する。
	 * 
	 * @author 寺田健大 -Task25
	 * @return Boolean 未入力日がある場合(件数が0より大きい)はtrue、そうでない場合はfalse
	 * @throws ParseException 日付パース時の例外
	 */
	public Boolean notEnterCheck() throws ParseException {

		//寺田健大 -Task25
		//今日の日付を取得する(フォーマットパターンを設定)
		SimpleDateFormat sdf = new SimpleDateFormat(Constants.DEFAULT_DATE_FORMAT);

		//寺田健大 -Task25
		//formatメソッドから現在日付を取得する
		//String型で受け取った現在日付をparseメソッドでDate型に変換
		Date trainingDate = sdf.parse(sdf.format(new Date()));

		//寺田健大 -Task25
		//勤怠情報(受講生入力)APIを呼び出し、過去日の未入力件数を取得
		//引数:loginUserDtoからLMSユーザIDを取得、削除フラグをutil.Constantsから取得(0)、現在日付を設定
		Integer count = tStudentAttendanceMapper.notEnterCount(
				loginUserDto.getLmsUserId(),
				Constants.DB_FLG_FALSE,
				trainingDate);

		//寺田健大 -Task25
		//件数が0より大きければtrue、そうでなければfalseを戻す
		if (count != null && count > 0) {
			return true;
		}

		return false;
	}

	/**
	 * 画面から入力された時・分のデータをDB保存用の「hh:mm」形式に変換してセットする。
	 * 
	 * @author 寺田健大 -Task26
	 * @param attendanceForm 勤怠一覧画面のフォームオブジェクト
	 */
	public void formatConversion(AttendanceForm attendanceForm) {

		//寺田健大 -Task26
		// 念のため null チェック(リストが空でなければ処理を継続)
		if (attendanceForm == null || attendanceForm.getAttendanceList() == null) {
			return;
		}

		//寺田健大 -Task26
		//日別で時間をセットする
		for (DailyAttendanceForm dailyForm : attendanceForm.getAttendanceList()) {

			//寺田健大 -Task26
			// 出勤の「時」「分」が共に入力されている場合のみ、%02d:%02d形式でセット
			if (dailyForm.getStartTimeHour() != null && dailyForm.getStartTimeMinute() != null) {
				String formattedStart = String.format("%02d:%02d", dailyForm.getStartTimeHour(),
						dailyForm.getStartTimeMinute());
				dailyForm.setTrainingStartTime(formattedStart);
			}

			//寺田健大 -Task26
			// 退勤の「時」「分」が共に入力されている場合のみ、%02d:%02d形式でセット
			if (dailyForm.getEndTimeHour() != null && dailyForm.getEndTimeMinute() != null) {
				String formattedEnd = String.format("%02d:%02d", dailyForm.getEndTimeHour(),
						dailyForm.getEndTimeMinute());
				dailyForm.setTrainingEndTime(formattedEnd);
			}
		}
	}

	/**
	 * 勤怠入力データの一括バリデーションチェック
	 * 
	 * @author 寺田健大 -Task27
	 * @param attendanceForm 勤怠一覧画面のフォームオブジェクト
	 * @param result エラー情報を格納するBindingResult
	 */
	public void updateInputCheck(AttendanceForm attendanceForm, BindingResult result) {

		//寺田健大 -Task27
		//ガード句による不正データの早期リターン
		if (attendanceForm == null || attendanceForm.getAttendanceList() == null) {
			return;
		}

		int count = 0;
		boolean hasAnyError = false;

		//勤怠明細一覧を1日ずつループしてチェック
		for (DailyAttendanceForm dailyForm : attendanceForm.getAttendanceList()) {

			//寺田健大 -Task27
			//備考欄の文字数チェック(100文字上限)
			if (dailyForm.getNote() != null && dailyForm.getNote().length() > 100) {
				result.rejectValue("attendanceList[" + count + "].note", "maxlength",
						new Object[] { "備考", 100 }, null);
				hasAnyError = true;
			}

			//各パーツ(時・分)の入力有無を判定するフラグ
			boolean hasStartHour = (dailyForm.getStartTimeHour() != null);
			boolean hasStartMinute = (dailyForm.getStartTimeMinute() != null);
			boolean hasEndHour = (dailyForm.getEndTimeHour() != null);
			boolean hasEndMinute = (dailyForm.getEndTimeMinute() != null);

			//寺田健大 -Task27
			//出勤時間の「時・分」の片方のみ入力されている場合の個別エラー装飾
			if (hasStartHour != hasStartMinute) {
				if (!hasStartHour) {
					result.rejectValue("attendanceList[" + count + "].startTimeHour", "input.invalid",
							new Object[] { "出勤時間" }, null);
				} else {
					result.rejectValue("attendanceList[" + count + "].startTimeMinute", "input.invalid",
							new Object[] { "出勤時間" }, null);
				}
				hasAnyError = true;
			}

			//寺田健大 -Task27
			//退勤時間の「時・分」の片方のみ入力されている場合の個別エラー装飾
			if (hasEndHour != hasEndMinute) {
				if (!hasEndHour) {
					result.rejectValue("attendanceList[" + count + "].endTimeHour", "input.invalid",
							new Object[] { "退勤時間" }, null);
				} else {
					result.rejectValue("attendanceList[" + count + "].endTimeMinute", "input.invalid",
							new Object[] { "退勤時間" }, null);
				}
				hasAnyError = true;
			}

			boolean isStartEntered = (hasStartHour && hasStartMinute);
			boolean isEndEntered = (hasEndHour && hasEndMinute);

			//寺田健大 -Task27
			//出勤未入力かつ退勤のみ入力されている場合の相関エラーチェック
			if (!isStartEntered && isEndEntered) {
				result.rejectValue("attendanceList[" + count + "].startTimeHour", "attendance.punchInEmpty",
						new Object[] { "出勤時間" }, null);
				result.rejectValue("attendanceList[" + count + "].startTimeMinute", "attendance.punchInEmpty",
						new Object[] { "出勤時間" }, null);
				hasAnyError = true;
				isStartEntered = false;
			}

			//各時間パーツの個別エラーの有無を検知
			boolean hasTimeFieldError = result.hasFieldErrors("attendanceList[" + count + "].startTimeHour")
					|| result.hasFieldErrors("attendanceList[" + count + "].startTimeMinute")
					|| result.hasFieldErrors("attendanceList[" + count + "].endTimeHour")
					|| result.hasFieldErrors("attendanceList[" + count + "].endTimeMinute");

			//寺田健大 -Task27
			//出勤時刻 ≧ 退勤時刻 の矛盾チェックと画面メッセージ重複防止制御
			if (!hasTimeFieldError && isStartEntered && isEndEntered) {
				if (dailyForm.getTrainingStartTime() != null && dailyForm.getTrainingEndTime() != null) {
					LocalTime startTime = LocalTime.parse(dailyForm.getTrainingStartTime());
					LocalTime endTime = LocalTime.parse(dailyForm.getTrainingEndTime());
					if (startTime.isAfter(endTime) || startTime.equals(endTime)) {

						//出勤側にメッセージ付きエラーを登録して画面に出力
						result.rejectValue("attendanceList[" + count + "].startTimeHour",
								"attendance.trainingTimeRange", new Object[] { "出勤時刻", "退勤時刻" }, null);

						//退勤側は空文字のエラーを個別登録し、画面上の二重出力を防ぎつつプルダウンのみを赤く染める
						result.addError(new org.springframework.validation.FieldError(result.getObjectName(),
								"attendanceList[" + count + "].endTimeHour", ""));

						hasTimeFieldError = true;
						hasAnyError = true;
					}
				}
			}

			//寺田健大 -Task27
			//中抜け時間が勤務可能時間を超えているかの相関エラーチェック
			if (!hasTimeFieldError && isStartEntered && isEndEntered && dailyForm.getBlankTime() != null) {
				if (dailyForm.getTrainingStartTime() != null && dailyForm.getTrainingEndTime() != null) {
					LocalTime startTime = LocalTime.parse(dailyForm.getTrainingStartTime());
					LocalTime endTime = LocalTime.parse(dailyForm.getTrainingEndTime());

					long maxTrainingMinutes = Duration.between(startTime, endTime).toMinutes();
					int blankMinutes = dailyForm.getBlankTime();

					if (blankMinutes >= maxTrainingMinutes) {
						result.rejectValue("attendanceList[" + count + "].blankTime", "attendance.blankTimeError",
								new Object[] { "中抜け時間" }, null);
						hasAnyError = true;
					}
				}
			}

			count++;
		}

		//寺田健大 -Task27
		//エラー発生時、入力途中のマッピング用マップデータを再保持して画面崩れを防止
		if (hasAnyError) {
			attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
			attendanceForm.setHourMap(attendanceUtil.getHourMap());
			attendanceForm.setMinuteMap(attendanceUtil.getMinuteMap());
		}
	}
}
