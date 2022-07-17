// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.kotlinRejecters.notification

import com.intellij.feedback.common.notification.RequestFeedbackNotification
import com.intellij.feedback.kotlinRejecters.bundle.KotlinRejectersFeedbackBundle

class KotlinRejectersFeedbackNotification : RequestFeedbackNotification(
  "Kotlin Rejecters Feedback In IDE",
  KotlinRejectersFeedbackBundle.message("notification.kotlin.feedback.request.feedback.title"),
  KotlinRejectersFeedbackBundle.message("notification.kotlin.feedback.request.feedback.content")
)