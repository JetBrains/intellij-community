// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.notification

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Basic notification for Kotlin feedback requests
 */

class RequestFeedbackNotification : Notification(
  "Feedback Notification",
  FeedbackBundle.message("notification.request.feedback.title"),
  FeedbackBundle.message("notification.request.feedback.content"),
  NotificationType.INFORMATION
) {

  //Tracking showing notification
  override fun notify(project: Project?) {
    //TODO: Should we track it?
    //val feedbackDatesState: FeedbackDatesState = service<FeedbackDatesService>().state ?: return
    super.notify(project)
    //feedbackDatesState.dateShowFeedbackNotification = LocalDate.now()
  }
}