// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.editor.ux

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

@Suppress("ActionPresentationInstantiatedInCtor")
internal class ShowEditorUxFeedbackNotificationAction
  : DumbAwareAction(EditorUxFeedbackBundle.messagePointer("show.editor.ux.feedback.notification")) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    EditorUxFeedbackSurvey().showNotification(project, forTest = true)
  }
}
