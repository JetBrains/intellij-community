// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.feedback.dialog.ProjectCreationFeedbackDialog
import com.intellij.feedback.notification.RequestFeedbackNotification
import com.intellij.feedback.show.isIntellijIdeaEAP
import com.intellij.feedback.statistics.ProjectCreationFeedbackCountCollector
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

object FeedbackTypeResolver {
  private const val requiredIdeaEapVersion: Boolean = true

  private const val isProjectCreationNotificationEnabledPropertyName = "isProjectCreationNotificationEnabled"
  private var isProjectCreationNotificationEnabled
    get() = PropertiesComponent.getInstance().getBoolean(isProjectCreationNotificationEnabledPropertyName, true)
    set(value) {
      PropertiesComponent.getInstance().setValue(isProjectCreationNotificationEnabledPropertyName, value)
    }

  fun showProjectCreationFeedbackNotification(project: Project, createdProjectTypeName: String) {
    if (isProjectCreationNotificationEnabled && (!requiredIdeaEapVersion || (requiredIdeaEapVersion && isIntellijIdeaEAP()))) {
      val notification = createProjectCreationFeedbackNotification(project, createdProjectTypeName)
      notification.notify(project)
    }
  }

  fun createProjectCreationFeedbackNotification(project: Project?, createdProjectTypeName: String): Notification {
    val notification = RequestFeedbackNotification()
    notification.addAction(
      NotificationAction.createSimple(FeedbackBundle.message("notification.request.feedback.action.respond.text")) {
        ProjectCreationFeedbackCountCollector.logNotificationActionCalled()
        val dialog = ProjectCreationFeedbackDialog(project, createdProjectTypeName)
        dialog.show()
        if (dialog.exitCode == DialogWrapper.CLOSE_EXIT_CODE) {
          ProjectCreationFeedbackCountCollector.logDialogClosed()
        }
      }
    )
    notification.addAction(
      NotificationAction.createSimpleExpiring(
        FeedbackBundle.message("notification.request.feedback.action.dont.show.text")) {
        isProjectCreationNotificationEnabled = false
      }
    )
    return notification
  }
}