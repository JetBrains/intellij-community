// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.feedback.dialog.ProjectCreationFeedbackDialog
import com.intellij.feedback.notification.RequestFeedbackNotification
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TestShowNewProjectFeedbackDialogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val notification = RequestFeedbackNotification()
    notification.addAction(
      object : NotificationAction(FeedbackBundle.message("notification.request.feedback.action.text")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          ProjectCreationFeedbackDialog(e.project, "TEST").show()
        }
      })
    notification.notify(e.project)
  }
}