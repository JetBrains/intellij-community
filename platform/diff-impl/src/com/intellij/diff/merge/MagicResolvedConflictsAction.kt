// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.project.DumbAwareAction

internal class MagicResolvedConflictsAction(private val viewer: MergeThreesideViewer) : DumbAwareAction() {
  init {
    copyFrom(this, "Diff.MagicResolveConflicts")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.setEnabled(viewer.model.hasAutoResolvableConflictedChanges() && !viewer.isExternalOperationInProgress)
  }

  override fun actionPerformed(e: AnActionEvent) {
    viewer.applyResolvableConflictedChanges()
  }
}
