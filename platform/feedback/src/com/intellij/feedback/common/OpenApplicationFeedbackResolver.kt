// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.kotlinRejecters.dialog.KotlinRejectersFeedbackDialog
import com.intellij.feedback.kotlinRejecters.notification.KotlinRejectersFeedbackNotification
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.feedback.kotlinRejecters.state.KotlinRejectersInfoService
import com.intellij.notification.NotificationAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.PlatformUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayAt

internal class OpenApplicationFeedbackShower : AppLifecycleListener {
  companion object {
    private val LAST_DATE_COLLECT_FEEDBACK = LocalDate(2022, 8, 26)
    private const val MAX_NUMBER_NOTIFICATION_SHOWED = 1

    internal fun showNotification(project: Project?, forTest: Boolean) {
      val notification = KotlinRejectersFeedbackNotification()
      notification.addAction(
        NotificationAction.createSimpleExpiring(CommonFeedbackBundle.message("notification.request.feedback.action.respond.text")) {
          val dialog = KotlinRejectersFeedbackDialog(project, forTest)
          dialog.show()
        }
      )
      notification.addAction(
        NotificationAction.createSimpleExpiring(CommonFeedbackBundle.message("notification.request.feedback.action.dont.show.text")) {
          if (!forTest) {
            IdleFeedbackTypeResolver.isFeedbackNotificationDisabled = true
          }
        }
      )
      notification.notify(project)
    }
  }

  override fun appStarted() {
    if (!PlatformUtils.isIdeaUltimate() && !PlatformUtils.isIdeaCommunity()) {
      return
    }
    //Try to show only one possible feedback form - Kotlin Rejecters form
    val kotlinRejectersInfoState = KotlinRejectersInfoService.getInstance().state
    if (!kotlinRejectersInfoState.feedbackSent && kotlinRejectersInfoState.showNotificationAfterRestart &&
        LAST_DATE_COLLECT_FEEDBACK >= Clock.System.todayAt(TimeZone.currentSystemDefault()) &&
        !IdleFeedbackTypeResolver.isFeedbackNotificationDisabled &&
        kotlinRejectersInfoState.numberNotificationShowed < MAX_NUMBER_NOTIFICATION_SHOWED) {
      val project = ProjectManager.getInstance().openProjects.firstOrNull()
      kotlinRejectersInfoState.numberNotificationShowed += 1
      showNotification(project, false)
    }
  }
}