// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.new_ui

import com.intellij.feedback.new_ui.bundle.NewUIFeedbackBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType

class CancelFeedbackNotification : Notification(
  "Feedback In IDE",
  "",
  NewUIFeedbackBundle.message("notification.cancel.feedback.content"),
  NotificationType.INFORMATION
)