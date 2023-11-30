// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl.notification

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle

open class ThanksForFeedbackNotification(
  @NlsSafe title: String = CommonFeedbackBundle.message("notification.thanks.feedback.title"),
  @NlsSafe description: String = CommonFeedbackBundle.message("notification.thanks.feedback.content")
) : Notification(
  "Feedback In IDE",
  title, description,
  NotificationType.INFORMATION
)