// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.notification

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType

class ThanksForFeedbackNotification : Notification(
  "Feedback Notification",
  FeedbackBundle.message("notification.thanks.feedback.title"),
  FeedbackBundle.message("notification.thanks.feedback.content"),
  NotificationType.INFORMATION
)