// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.notification.RequestFeedbackNotification
import com.intellij.feedback.disabledKotlinPlugin.bundle.DisabledKotlinPluginFeedbackBundle
import com.intellij.feedback.disabledKotlinPlugin.dialog.DisabledKotlinPluginFeedbackDialog
import com.intellij.ide.feedback.kotlinRejecters.state.KotlinRejectersInfoService
import com.intellij.notification.NotificationAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class OpenedProjectFeedbackShower : ProjectManagerListener {

  companion object {
    fun showNotification(project: Project?, forTest: Boolean) {
      //Try to show only one possible feedback form - Kotlin Rejecters form
      val kotlinRejectersInfoState = KotlinRejectersInfoService.getInstance().state
      if (!kotlinRejectersInfoState.feedbackSent && kotlinRejectersInfoState.showNotificationAfterRestart) {
        val notification = RequestFeedbackNotification(
          DisabledKotlinPluginFeedbackBundle.message("notification.kotlin.feedback.request.feedback.title"),
          DisabledKotlinPluginFeedbackBundle.message("notification.kotlin.feedback.request.feedback.content")
        )
        notification.addAction(
          NotificationAction.createSimpleExpiring(CommonFeedbackBundle.message("notification.request.feedback.action.respond.text")) {
            val dialog = DisabledKotlinPluginFeedbackDialog(project, forTest)
            dialog.show()
          }
        )
        notification.notify(project)
        notification.addAction(
          NotificationAction.createSimpleExpiring(CommonFeedbackBundle.message("notification.request.feedback.action.dont.show.text")) {
            if (!forTest) {
              IdleFeedbackTypeResolver.isFeedbackNotificationDisabled = true
            }
          }
        )
      }
    }
  }

  override fun projectOpened(project: Project) {
    showNotification(project, false)
  }
}