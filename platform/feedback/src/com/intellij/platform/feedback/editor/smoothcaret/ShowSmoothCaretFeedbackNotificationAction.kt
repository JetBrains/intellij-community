// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.editor.smoothcaret

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ShowSmoothCaretFeedbackNotificationAction : DumbAwareAction() {
  
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    SmoothCaretFeedbackSurvey().showNotification(project, forTest = true)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
