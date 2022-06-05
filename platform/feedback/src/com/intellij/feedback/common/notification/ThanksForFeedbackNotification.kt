// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.notification

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType

open class ThanksForFeedbackNotification : Notification(
  "Feedback In IDE",
  CommonFeedbackBundle.message("notification.thanks.feedback.title"),
  CommonFeedbackBundle.message("notification.thanks.feedback.content"),
  NotificationType.INFORMATION
)