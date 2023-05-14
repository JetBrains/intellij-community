// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common

import com.intellij.feedback.aqua.bundle.AquaFeedbackBundle
import com.intellij.feedback.aqua.dialog.AquaNewUserFeedbackDialog
import com.intellij.feedback.aqua.dialog.AquaOldUserFeedbackDialog
import com.intellij.feedback.aqua.state.AquaNewUserFeedbackService
import com.intellij.feedback.aqua.state.AquaNewUserInfoState
import com.intellij.feedback.aqua.state.AquaOldUserFeedbackService
import com.intellij.feedback.aqua.state.AquaOldUserInfoState
import com.intellij.feedback.common.IdleFeedbackTypeResolver.isFeedbackNotificationDisabled
import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.notification.RequestFeedbackNotification
import com.intellij.feedback.common.statistics.FeedbackNotificationCountCollector.Companion.logDisableNotificationActionInvoked
import com.intellij.feedback.common.statistics.FeedbackNotificationCountCollector.Companion.logRequestNotificationShown
import com.intellij.feedback.common.statistics.FeedbackNotificationCountCollector.Companion.logRespondNotificationActionInvoked
import com.intellij.feedback.new_ui.CancelFeedbackNotification
import com.intellij.feedback.new_ui.bundle.NewUIFeedbackBundle
import com.intellij.feedback.new_ui.dialog.NewUIFeedbackDialog
import com.intellij.feedback.new_ui.state.NewUIInfoService
import com.intellij.feedback.new_ui.state.NewUIInfoState
import com.intellij.feedback.pycharmUi.bundle.PyCharmUIFeedbackBundle
import com.intellij.feedback.pycharmUi.dialog.PyCharmUIFeedbackDialog
import com.intellij.feedback.pycharmUi.state.PyCharmUIInfoService
import com.intellij.feedback.pycharmUi.state.PyCharmUIInfoState
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.PlatformUtils
import kotlinx.datetime.*
import java.time.Duration
import java.time.LocalDateTime

enum class IdleFeedbackTypes {
  NEW_UI_FEEDBACK {
    override val fusFeedbackId: String = "new_ui_feedback"
    override val suitableIdeVersion: String = "2022.3"
    private val lastDayCollectFeedback = LocalDate(2022, 12, 6)
    private val maxNumberNotificationShowed = 1
    private val minNumberDaysElapsed = 5

    override fun isSuitable(): Boolean {
      val newUIInfoState = NewUIInfoService.getInstance().state

      return checkIdeIsSuitable() &&
             checkIsNoDeadline() &&
             checkIdeVersionIsSuitable() &&
             checkFeedbackNotSent(newUIInfoState) &&
             checkNewUIHasBeenEnabled(newUIInfoState) &&
             checkNotificationNumberNotExceeded(newUIInfoState)
    }

    private fun checkIdeIsSuitable(): Boolean {
      return !PlatformUtils.isRider()
    }

    private fun checkIsNoDeadline(): Boolean {
      return Clock.System.todayIn(TimeZone.currentSystemDefault()) < lastDayCollectFeedback
    }

    private fun checkFeedbackNotSent(state: NewUIInfoState): Boolean {
      return !state.feedbackSent
    }

    private fun checkNewUIHasBeenEnabled(state: NewUIInfoState): Boolean {
      val enableNewUIDate = state.enableNewUIDate
      if (enableNewUIDate == null) {
        return false
      }

      return Duration.between(enableNewUIDate.toJavaLocalDateTime(), LocalDateTime.now()).toDays() >= minNumberDaysElapsed
    }

    private fun checkNotificationNumberNotExceeded(state: NewUIInfoState): Boolean {
      return state.numberNotificationShowed < maxNumberNotificationShowed
    }

    override fun createNotification(forTest: Boolean): Notification {
      return RequestFeedbackNotification(
        "Feedback In IDE",
        NewUIFeedbackBundle.message("notification.request.feedback.title"),
        NewUIFeedbackBundle.message("notification.request.feedback.content"))
    }

    override fun createFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper {
      return NewUIFeedbackDialog(project, forTest)
    }

    override fun updateStateAfterNotificationShowed() {
      NewUIInfoService.getInstance().state.numberNotificationShowed += 1
    }

    override fun updateStateAfterDialogClosedOk() {
      NewUIInfoService.getInstance().state.feedbackSent = true
    }

    override fun getGiveFeedbackNotificationLabel(): String {
      return NewUIFeedbackBundle.getMessage("notification.request.feedback.give_feedback")
    }

    override fun getCancelFeedbackNotificationLabel(): String {
      return NewUIFeedbackBundle.getMessage("notification.request.feedback.cancel.feedback")
    }

    override fun getNotificationOnCancelAction(project: Project?): () -> Unit {
      return { CancelFeedbackNotification().notify(project) }
    }
  },
  PYCHARM_UI_FEEDBACK {
    override val fusFeedbackId: String = "pycharm_ui_feedback"
    override val suitableIdeVersion: String = "2023.1"
    private val lastDayCollectFeedback = LocalDate(2023, 7, 25)
    private val maxNumberNotificationShowed = 1
    private val elapsedMinNumberDaysFromFirstRun = 5

    override fun isSuitable(): Boolean {
      val state = PyCharmUIInfoService.getInstance().state

      return checkIdeIsSuitable() &&
             checkIsNoDeadline() &&
             checkIdeVersionIsSuitable() &&
             checkFeedbackNotSent(state) &&
             checkNotificationNumberNotExceeded(state) &&
             checkNewUserFirstRunBeforeTime(state.newUserFirstRunDate)
    }

    override fun createNotification(forTest: Boolean): Notification {
      return RequestFeedbackNotification(
        "Feedback In IDE",
        PyCharmUIFeedbackBundle.message("notification.request.feedback.title"),
        PyCharmUIFeedbackBundle.message("notification.request.feedback.content"))
    }

    override fun createFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper {
      return PyCharmUIFeedbackDialog(project, forTest)
    }

    override fun updateStateAfterNotificationShowed() {
      PyCharmUIInfoService.getInstance().state.numberNotificationShowed += 1
    }

    override fun updateStateAfterDialogClosedOk() {
      PyCharmUIInfoService.getInstance().state.feedbackSent = true
    }

    private fun checkIdeIsSuitable(): Boolean {
      return PlatformUtils.isPyCharmCommunity()
    }

    private fun checkIsNoDeadline(): Boolean {
      return Clock.System.todayIn(TimeZone.currentSystemDefault()) < lastDayCollectFeedback
    }

    private fun checkFeedbackNotSent(state: PyCharmUIInfoState): Boolean {
      return !state.feedbackSent
    }

    private fun checkNotificationNumberNotExceeded(state: PyCharmUIInfoState): Boolean {
      return state.numberNotificationShowed < maxNumberNotificationShowed
    }

    private fun checkNewUserFirstRunBeforeTime(newUserFirstRunDate: kotlinx.datetime.LocalDateTime?): Boolean {
      if (newUserFirstRunDate == null) {
        return false
      }

      return Duration.between(newUserFirstRunDate.toJavaLocalDateTime(), LocalDateTime.now()).toDays() >= elapsedMinNumberDaysFromFirstRun
    }
  },
  AQUA_NEW_USER_FEEDBACK {
    override val fusFeedbackId: String = "aqua_new_user_feedback"
    override val suitableIdeVersion: String = "" // Not suitable for Aqua, because it is in the permanent Preview version
    private val lastDayCollectFeedback = LocalDate(2023, 7, 1)
    private val maxNumberNotificationShowed = 2

    override fun isSuitable(): Boolean {
      val state = AquaNewUserFeedbackService.getInstance().state

      return checkIdeIsSuitable() &&
             checkIsNoDeadline() &&
             isAnyProjectOpenNow() &&
             isUserTypedInEditor(state) &&
             checkFeedbackNotSent(state) &&
             checkNotificationNumberNotExceeded(state)
    }

    override fun createNotification(forTest: Boolean): Notification {
      return RequestFeedbackNotification(
        "Feedback In IDE",
        AquaFeedbackBundle.message("new.user.notification.request.feedback.title"),
        AquaFeedbackBundle.message("new.user.notification.request.feedback.content"))
    }

    override fun createFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper {
      return AquaNewUserFeedbackDialog(project, forTest)
    }

    override fun updateStateAfterNotificationShowed() {
      AquaNewUserFeedbackService.getInstance().state.numberNotificationShowed += 1
    }

    override fun updateStateAfterDialogClosedOk() {
      AquaNewUserFeedbackService.getInstance().state.feedbackSent = true
    }

    private fun checkIdeIsSuitable(): Boolean {
      return PlatformUtils.isAqua()
    }

    private fun isAnyProjectOpenNow(): Boolean {
      return ProjectManager.getInstance().openProjects.count {
        !it.isDisposed
      } > 0
    }

    private fun isUserTypedInEditor(state: AquaNewUserInfoState): Boolean {
      return state.userTypedInEditor
    }

    private fun checkIsNoDeadline(): Boolean {
      return Clock.System.todayIn(TimeZone.currentSystemDefault()) < lastDayCollectFeedback
    }

    private fun checkFeedbackNotSent(state: AquaNewUserInfoState): Boolean {
      return !state.feedbackSent
    }

    private fun checkNotificationNumberNotExceeded(state: AquaNewUserInfoState): Boolean {
      return state.numberNotificationShowed < maxNumberNotificationShowed
    }

  },
  AQUA_OLD_USER_FEEDBACK {
    override val fusFeedbackId: String = "aqua_old_user_feedback"
    override val suitableIdeVersion: String = "" // Not suitable for Aqua, because it is in the permanent Preview version
    private val lastDayCollectFeedback = LocalDate(2023, 7, 1)
    private val maxNumberNotificationShowed = 2
    private val elapsedMinNumberDaysFromFirstRun = 5

    override fun isSuitable(): Boolean {
      val state = AquaOldUserFeedbackService.getInstance().state

      return checkIdeIsSuitable() &&
             checkIsNoDeadline() &&
             isAnyProjectOpenNow() &&
             isUserTypedInEditor(state) &&
             isUsageTimeEnough(state) &&
             checkFeedbackNotSent(state) &&
             checkNotificationNumberNotExceeded(state)
    }

    override fun createNotification(forTest: Boolean): Notification {
      return RequestFeedbackNotification(
        "Feedback In IDE",
        AquaFeedbackBundle.message("old.user.notification.request.feedback.title"),
        AquaFeedbackBundle.message("old.user.notification.request.feedback.content"))
    }

    override fun createFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper {
      return AquaOldUserFeedbackDialog(project, forTest)
    }

    override fun updateStateAfterNotificationShowed() {
      AquaOldUserFeedbackService.getInstance().state.numberNotificationShowed += 1
    }

    override fun updateStateAfterDialogClosedOk() {
      AquaOldUserFeedbackService.getInstance().state.feedbackSent = true
    }

    private fun checkIdeIsSuitable(): Boolean {
      return PlatformUtils.isAqua()
    }

    private fun checkIsNoDeadline(): Boolean {
      return Clock.System.todayIn(TimeZone.currentSystemDefault()) < lastDayCollectFeedback
    }

    private fun isAnyProjectOpenNow(): Boolean {
      return ProjectManager.getInstance().openProjects.count {
        !it.isDisposed
      } > 0
    }

    private fun isUserTypedInEditor(state: AquaOldUserInfoState): Boolean {
      return state.userTypedInEditor
    }

    private fun isUsageTimeEnough(state: AquaOldUserInfoState): Boolean {
      val firstUsageTime = state.firstUsageTime
      if (firstUsageTime == null) {
        return false
      }
      return Duration.between(firstUsageTime.toJavaLocalDateTime(), LocalDateTime.now()).toDays() >= elapsedMinNumberDaysFromFirstRun
    }

    private fun checkFeedbackNotSent(state: AquaOldUserInfoState): Boolean {
      return !state.feedbackSent
    }

    private fun checkNotificationNumberNotExceeded(state: AquaOldUserInfoState): Boolean {
      return state.numberNotificationShowed < maxNumberNotificationShowed
    }

  };

  protected abstract val fusFeedbackId: String

  protected abstract val suitableIdeVersion: String

  abstract fun isSuitable(): Boolean

  protected fun isIdeEAP(): Boolean {
    return ApplicationInfoEx.getInstanceEx().isEAP
  }

  protected fun checkIdeVersionIsSuitable(): Boolean {
    return suitableIdeVersion == ApplicationInfoEx.getInstanceEx().shortVersion
  }

  protected abstract fun createNotification(forTest: Boolean): Notification

  protected abstract fun createFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper

  protected abstract fun updateStateAfterNotificationShowed()

  protected abstract fun updateStateAfterDialogClosedOk()

  @NlsSafe
  protected open fun getGiveFeedbackNotificationLabel(): String {
    return CommonFeedbackBundle.message("notification.request.feedback.action.respond.text")
  }

  @NlsSafe
  protected open fun getCancelFeedbackNotificationLabel(): String {
    return CommonFeedbackBundle.message("notification.request.feedback.action.dont.show.text")
  }

  protected open fun getNotificationOnCancelAction(project: Project?): () -> Unit {
    return {}
  }

  fun showNotification(project: Project?, forTest: Boolean = false) {
    val notification = createNotification(forTest)
    notification.addAction(
      NotificationAction.createSimpleExpiring(getGiveFeedbackNotificationLabel()) {
        if (!forTest) {
          logRespondNotificationActionInvoked(this)
        }
        val dialog = createFeedbackDialog(project, forTest)
        val isOk = dialog.showAndGet()
        if (isOk) {
          updateStateAfterDialogClosedOk()
        }
      }
    )
    notification.addAction(
      NotificationAction.createSimpleExpiring(getCancelFeedbackNotificationLabel()) {
        if (!forTest) {
          isFeedbackNotificationDisabled = true
          logDisableNotificationActionInvoked(this)
        }
        getNotificationOnCancelAction(project)()
      }
    )
    notification.notify(project)
    if (!forTest) {
      logRequestNotificationShown(this)
      updateStateAfterNotificationShowed()
    }
  }
}