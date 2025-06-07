// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.runCell

import com.intellij.icons.AllIcons
import com.intellij.notebooks.ui.afterDistinctChange
import com.intellij.notebooks.visualization.NotebookCellLines.CellType
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedCellController
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.ProgressStatus
import com.intellij.notebooks.visualization.ui.notebook
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareAction.SimpleDumbAwareAction

class EditorCellRunGutterController(
  private val cell: EditorCell,
) : SelfManagedCellController {
  private val editor = cell.editor

  private val runAction = SimpleDumbAwareAction.create(AllIcons.Actions.Execute) {
    val cellLineOffset = cell.interval.lines.first
    editor.caretModel.moveToOffset(editor.document.getLineStartOffset(cellLineOffset))
    val runCellAction = ActionManager.getInstance().getAction("NotebookRunCellAction")
    runCellAction.actionPerformed(it)
  }

  private val stopAction = SimpleDumbAwareAction.create(AllIcons.Run.Stop) {
    val interruptKernelAction = ActionManager.getInstance().getAction("JupyterInterruptKernelAction")
    interruptKernelAction.actionPerformed(it)
  }

  init {
    cell.isSelected.afterDistinctChange(this) {
      updateGutterAction()
    }
    cell.isHovered.afterDistinctChange(this) {
      updateGutterAction()
    }
    cell.notebook.readOnly.afterDistinctChange(this) {
      updateGutterAction()
    }
    cell.executionStatus.afterDistinctChange(this) {
      updateGutterAction()
    }
    updateGutterAction()
  }

  override fun dispose() {
    cell.gutterAction.set(null)
  }

  override fun checkAndRebuildInlays() {}

  private fun updateGutterAction() {
    //For markdown, it will set up in markdown component
    if (cell.type == CellType.MARKDOWN)
      return

    val newAction = calculateAction()

    val currentGutterAction = cell.gutterAction.get()
    if (newAction == currentGutterAction) {
      return
    }

    cell.gutterAction.set(newAction)
  }

  private fun calculateAction(): DumbAwareAction? {
    val isReadOnlyNotebook = editor.notebook?.readOnly?.get() == true
    if (isReadOnlyNotebook)
      return null

    val isHoveredOrSelected = cell.isHovered.get() || cell.isSelected.get()
    if (!isHoveredOrSelected) {
      return null
    }
    val status = cell.executionStatus.get().status
    if (status == ProgressStatus.RUNNING || status == ProgressStatus.QUEUED) {
      return stopAction
    }
    else {
      return runAction
    }
  }
}