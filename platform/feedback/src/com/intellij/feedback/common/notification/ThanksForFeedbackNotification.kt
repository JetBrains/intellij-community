// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.notification

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType

class ThanksForFeedbackNotification : Notification(
  "Feedback In IDE",
  FeedbackBundle.message("notification.thanks.feedback.title"),
  FeedbackBundle.message("notification.thanks.feedback.content"),
  NotificationType.INFORMATION
)