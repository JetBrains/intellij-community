// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl.notification

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.NlsSafe

/**
 * Basic notification for feedback requests
 */

open class RequestFeedbackNotification(groupId: String, @NlsSafe title: String, @NlsSafe content: String) : Notification(
  groupId,
  title, content,
  NotificationType.INFORMATION
) {
  init {
    isSuggestionType = true
  }
}