// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.wsl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import java.util.function.Supplier

/**
 * Shows the WSL satisfaction survey notification (bypassing the usual show conditions) so QA can check
 * both the notification wording and, via the "Respond" action, the dialog. Feedback is sent as a test request.
 */
@Suppress("ActionPresentationInstantiatedInCtor")
internal class ShowWslSatisfactionDialogAction :
  DumbAwareAction(Supplier { WslSatisfactionFeedbackBundle.message("action.ShowWslSatisfactionDialog.text") }) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    WslSatisfactionSurvey().showNotification(project, forTest = true)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
