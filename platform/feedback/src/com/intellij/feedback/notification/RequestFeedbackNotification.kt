// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.notification

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType

/**
 * Basic notification for Kotlin feedback requests
 */

class RequestFeedbackNotification : Notification(
  "Project Creation Feedback",
  FeedbackBundle.message("notification.request.feedback.title"),
  FeedbackBundle.message("notification.request.feedback.content"),
  NotificationType.INFORMATION
)