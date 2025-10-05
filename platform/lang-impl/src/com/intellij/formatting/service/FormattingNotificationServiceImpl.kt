// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service

import com.intellij.formatting.FormattingContext
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.application

private class FormattingNotificationServiceImpl(private val project: Project) : FormattingNotificationService {
  override fun reportError(
    groupId: String,
    displayId: String?,
    title: @NlsContexts.NotificationTitle String,
    message: @NlsContexts.NotificationContent String,
    vararg actions: AnAction,
  ) = logIfHeadlessOrRun(title, message) {
    FormattingNotificationUtil.reportError(project, groupId, displayId, title, message, *actions)
  }

  override fun reportErrorAndNavigate(
    groupId: String,
    displayId: String?,
    title: @NlsContexts.NotificationTitle String,
    message: @NlsContexts.NotificationContent String,
    context: FormattingContext,
    offset: Int,
  ) = logIfHeadlessOrRun(title, message) {
    FormattingNotificationUtil.reportErrorAndNavigate(project, groupId, displayId, title, message, context, offset)
  }
}

private inline fun <T> logIfHeadlessOrRun(
  title: @NlsContexts.NotificationTitle String,
  message: @NlsContexts.NotificationContent String,
  block: () -> T,
) {
  if (application.isHeadlessEnvironment) {
    System.err.println("$title: $message")
  }
  else {
    block()
  }
}