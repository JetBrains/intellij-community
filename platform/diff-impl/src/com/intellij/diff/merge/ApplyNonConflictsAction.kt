// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.Nls

internal class ApplyNonConflictsAction(
  private val viewer: MergeThreesideViewer,
  private val side: ThreeSide,
  text: @Nls String,
) : DumbAwareAction() {
  init {
    val id = side.select("Diff.ApplyNonConflicts.Left", "Diff.ApplyNonConflicts", "Diff.ApplyNonConflicts.Right")
    copyFrom(this, id)

    templatePresentation.apply {
      putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
      putClientProperty(ActionUtil.USE_SMALL_FONT_IN_TOOLBAR, true)
      this.text = text
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.setEnabled(viewer.model.hasNonConflictedChanges(side) && !viewer.isExternalOperationInProgress)
  }

  override fun actionPerformed(e: AnActionEvent) {
    viewer.applyNonConflictedChanges(side)
  }
}
