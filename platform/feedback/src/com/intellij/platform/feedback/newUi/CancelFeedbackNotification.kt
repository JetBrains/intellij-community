// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.newUi

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType

class CancelFeedbackNotification : Notification(
  "Feedback In IDE",
  "",
  NewUIFeedbackBundle.message("notification.cancel.feedback.content"),
  NotificationType.INFORMATION
)