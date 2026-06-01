// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages

internal class RevertConflictResolutionAction(private val viewer: MergeThreesideViewer) :
  DumbAwareAction(DiffBundle.message("action.merge.revert.conflict.resolution.text"),
                  DiffBundle.message("action.merge.revert.conflict.resolution.text"),
                  AllIcons.Diff.Revert) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = viewer.allChanges.any { it.isResolved } && !viewer.isExternalOperationInProgress
  }

  override fun actionPerformed(e: AnActionEvent) {
    val confirmed = MessageDialogBuilder
      .yesNo(
        DiffBundle.message("message.revert.conflict.resolution.dialog.title"),
        DiffBundle.message("message.revert.conflict.resolution.dialog.message")
      )
      .yesText(DiffBundle.message("message.revert.conflict.resolution.dialog.revert"))
      .noText(Messages.getCancelButton())
      .icon(Messages.getQuestionIcon())
      .ask(viewer.component)
    if (!confirmed) return

    viewer.resetChanges()
  }
}
