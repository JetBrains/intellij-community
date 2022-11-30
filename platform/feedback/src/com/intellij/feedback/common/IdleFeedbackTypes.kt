// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common

import com.intellij.feedback.common.IdleFeedbackTypeResolver.isFeedbackNotificationDisabled
import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.notification.RequestFeedbackNotification
import com.intellij.feedback.new_ui.CancelFeedbackNotification
import com.intellij.feedback.new_ui.bundle.NewUIFeedbackBundle
import com.intellij.feedback.new_ui.dialog.NewUIFeedbackDialog
import com.intellij.feedback.new_ui.state.NewUIInfoService
import com.intellij.feedback.new_ui.state.NewUIInfoState
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.PlatformUtils
import kotlinx.datetime.*
import java.time.Duration
import java.time.LocalDateTime

enum class IdleFeedbackTypes {
  NEW_UI_FEEDBACK {
    override val suitableIdeVersion: String = "2022.3"
    private val lastDayCollectFeedback = LocalDate(2022, 12, 31)
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

    override fun getGiveFeedbackNotificationLabel(): String {
      return NewUIFeedbackBundle.getMessage("notification.request.feedback.give_feedback")
    }

    override fun getCancelFeedbackNotificationLabel(): String {
      return NewUIFeedbackBundle.getMessage("notification.request.feedback.cancel.feedback")
    }

    override fun getNotificationOnCancelAction(project: Project?): () -> Unit {
      return { CancelFeedbackNotification().notify(project) }
    }
  };

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
        val dialog = createFeedbackDialog(project, forTest)
        dialog.show()
      }
    )
    notification.addAction(
      NotificationAction.createSimpleExpiring(getCancelFeedbackNotificationLabel()) {
        if (!forTest) {
          isFeedbackNotificationDisabled = true
        }
        getNotificationOnCancelAction(project)()
      }
    )
    notification.notify(project)
    if (!forTest) {
      updateStateAfterNotificationShowed()
    }
  }
}