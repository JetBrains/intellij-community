// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.feedback.dialog.ProjectCreationFeedbackDialog
import com.intellij.feedback.notification.RequestFeedbackNotification
import com.intellij.feedback.setting.FunctionalitySettings.enableNotification
import com.intellij.feedback.setting.FunctionalitySettings.requiredIdeaEapVersion
import com.intellij.feedback.show.isIntellijIdeaEAP
import com.intellij.feedback.statistics.ProjectCreationFeedbackCountCollector
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

object FeedbackTypeResolver {

  fun showProjectCreationFeedbackNotification(project: Project, createdProjectTypeName: String) {
    if (requiredIdeaEapVersion) {
      if (enableNotification && isIntellijIdeaEAP()) {
        val notification = createProjectCreationFeedbackNotification(project, createdProjectTypeName)
        notification.notify(project)
      }
    }
  }

  fun createProjectCreationFeedbackNotification(project: Project?, createdProjectTypeName: String): Notification {
    val notification = RequestFeedbackNotification()
    notification.addAction(
      object : NotificationAction(FeedbackBundle.message("notification.request.feedback.action.text")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          ProjectCreationFeedbackCountCollector.logNotificationActionCalled()
          val dialog = ProjectCreationFeedbackDialog(project, createdProjectTypeName)
          dialog.show()
          if (dialog.exitCode == DialogWrapper.CLOSE_EXIT_CODE) {
            ProjectCreationFeedbackCountCollector.logDialogClosed()
          }
        }
      })
    return notification
  }
}