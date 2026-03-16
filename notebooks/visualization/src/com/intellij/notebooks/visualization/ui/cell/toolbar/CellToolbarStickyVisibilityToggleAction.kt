// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.toolbar

import com.intellij.notebooks.visualization.settings.NotebookSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareToggleAction

internal class CellToolbarStickyVisibilityToggleAction : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun isSelected(e: AnActionEvent): Boolean {
    return NotebookSettings.getInstance().cellToolbarStickyVisible
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    NotebookSettings.getInstance().cellToolbarStickyVisible = state
  }
}