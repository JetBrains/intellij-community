// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ConfigImportHelper.hasPreviousVersionConfigDirs
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.platform.feedback.impl.state.DontShowAgainFeedbackService
import com.intellij.platform.feedback.impl.statistics.FeedbackNotificationCountCollector.logDisableNotificationActionInvoked
import com.intellij.platform.feedback.impl.statistics.FeedbackNotificationCountCollector.logRequestNotificationShown
import com.intellij.platform.feedback.impl.statistics.FeedbackNotificationCountCollector.logRespondNotificationActionInvoked
import com.intellij.platform.feedback.pycharmce.PyCharmCeFeedbackBundle
import com.intellij.platform.feedback.pycharmce.PyCharmCeFeedbackService
import com.intellij.platform.feedback.pycharmce.PyCharmCeFeedbackState
import com.intellij.util.PlatformUtils
import com.intellij.util.system.OS
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import java.util.*

@Deprecated("Instead use com.intellij.platform.feedback.FeedbackSurvey." +
            "For example see com.intellij.platform.feedback.demo.DemoExternalFeedbackSurvey and " +
            "com.intellij.platform.feedback.demo.DemoInIdeFeedbackSurvey")
enum class IdleFeedbackTypes {
  PYCHARM_CE_FEEDBACK {
    override val fusFeedbackId: String = "pycharm_ce_feedback"
    override val suitableIdeVersion: String = "2024.1"
    override fun getWebFormUrl(): String {
      val os = OS.CURRENT.name
      val locale = Locale.getDefault()
      val country = locale.country
      val lang = locale.language
      val ver = ApplicationInfo.getInstance().fullVersion
      return "https://surveys.jetbrains.com/s3/pc-ccs-24-1-3?os=${os}&country=${country}&lang=${lang}&ver=${ver}"
    }

    private val firstDayCollectFeedback: LocalDate = LocalDate(2024, Month.JULY, 1)
    private val lastDayCollectFeedback: LocalDate = LocalDate(2024, Month.JULY, 21)

    private val maxNumberNotificationShowed: Int = 1

    override fun isSuitable(): Boolean {
      val state = PyCharmCeFeedbackService.getInstance().state

      return checkIdeIsSuitable() &&
             checkIsNoDeadline() &&
             checkIdeVersionIsSuitable() &&
             checkFeedbackNotSent(state) &&
             checkNotificationNumberNotExceeded(state) &&
             checkIfExistingUserForSomeTime()
    }

    private fun checkIfExistingUserForSomeTime(): Boolean {
      return hasPreviousVersionConfigDirs()
    }

    private fun checkIdeIsSuitable(): Boolean {
      return PlatformUtils.isPyCharmCommunity()
    }

    private fun checkIsNoDeadline(): Boolean {
      val todayIn = Clock.System.todayIn(TimeZone.currentSystemDefault())
      return todayIn in firstDayCollectFeedback..lastDayCollectFeedback
    }

    private fun checkFeedbackNotSent(state: PyCharmCeFeedbackState): Boolean {
      return !state.feedbackSent
    }

    override fun createNotification(forTest: Boolean): Notification {
      return RequestFeedbackNotification(
        "Feedback In IDE",
        PyCharmCeFeedbackBundle.message("notification.request.feedback.title"),
        PyCharmCeFeedbackBundle.message("notification.request.feedback.content"))
    }

    override fun getGiveFeedbackNotificationLabel(): String {
      return PyCharmCeFeedbackBundle.getMessage("notification.request.feedback.give_feedback")
    }

    override fun getCancelFeedbackNotificationLabel(): String {
      return PyCharmCeFeedbackBundle.getMessage("notification.request.feedback.cancel.feedback")
    }

    override fun createFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper {
      throw UnsupportedOperationException() // web form URL is set
    }

    override fun updateStateAfterNotificationShowed() {
      PyCharmCeFeedbackService.getInstance().state.numberNotificationShowed += 1
    }

    override fun updateStateAfterDialogClosedOk() {
      PyCharmCeFeedbackService.getInstance().state.feedbackSent = true
    }

    private fun checkNotificationNumberNotExceeded(state: PyCharmCeFeedbackState): Boolean {
      return state.numberNotificationShowed < maxNumberNotificationShowed
    }
  };

  protected abstract val fusFeedbackId: String

  protected abstract val suitableIdeVersion: String

  abstract fun isSuitable(): Boolean

  protected fun isIdeEAP(): Boolean {
    return ApplicationInfo.getInstance().isEAP
  }

  protected fun checkIdeVersionIsSuitable(): Boolean {
    return suitableIdeVersion == ApplicationInfo.getInstance().shortVersion
  }

  protected abstract fun createNotification(forTest: Boolean): Notification

  protected abstract fun createFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper

  protected abstract fun updateStateAfterNotificationShowed()

  protected abstract fun updateStateAfterDialogClosedOk()

  protected open fun getWebFormUrl(): String? = null

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

        val url = getWebFormUrl()

        if (url == null) {
          val dialog = createFeedbackDialog(project, forTest)
          val isOk = dialog.showAndGet()
          if (isOk) {
            updateStateAfterDialogClosedOk()
          }
        }
        else {
          BrowserUtil.browse(url)
          updateStateAfterDialogClosedOk()
        }
        getNotificationOnCancelAction(project)()
      }
    )
    notification.addAction(
      NotificationAction.createSimpleExpiring(getCancelFeedbackNotificationLabel()) {
        if (!forTest) {
          DontShowAgainFeedbackService.dontShowFeedbackInCurrentVersion()
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