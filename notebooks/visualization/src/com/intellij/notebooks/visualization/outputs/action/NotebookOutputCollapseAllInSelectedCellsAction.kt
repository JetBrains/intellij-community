// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.outputs.action

import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.cellSelectionModel
import com.intellij.notebooks.visualization.context.NotebookDataContext.NOTEBOOK_CELL_OUTPUT_DATA_KEY
import com.intellij.notebooks.visualization.context.NotebookDataContext.notebookEditor
import com.intellij.notebooks.visualization.context.NotebookDataContext.selectedCellIntervals
import com.intellij.notebooks.visualization.ui.EditorCellOutput
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAware

internal class NotebookOutputCollapseAllAction private constructor() : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.notebookEditor != null
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    allCollapsingComponents(e).all { it.size.get().collapsed }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    for (component in allCollapsingComponents(e)) {
      component.size.set(component.size.get().copy(collapsed = state))
    }
  }

  private fun allCollapsingComponents(e: AnActionEvent): Sequence<EditorCellOutput> {
    val inlayManager = e.notebookCellInlayManager ?: return emptySequence()
    return getCollapsingComponents(inlayManager.editor, NotebookCellLines.get(inlayManager.editor).intervals)
  }
}

// same as Collapse All Action, but collapse outputs of selected cells
open class NotebookOutputCollapseAllInSelectedCellsAction() : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val editor = e.notebookEditor
    e.presentation.isEnabled = editor != null
    e.presentation.isVisible = editor?.cellSelectionModel?.let { it.selectedCells.size > 1 } == true
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    getSelectedCollapsingComponents(e).all { it.size.get().collapsed }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    for (component in getSelectedCollapsingComponents(e)) {
      component.size.set(component.size.get().copy(collapsed = state))
    }
  }

  private fun getSelectedCollapsingComponents(e: AnActionEvent): Sequence<EditorCellOutput> {
    val inlayManager = e.notebookCellInlayManager ?: return emptySequence()
    val selectedCells = inlayManager.editor.cellSelectionModel?.selectedCells ?: return emptySequence()
    return getCollapsingComponents(inlayManager.editor, selectedCells)
  }
}

internal class NotebookOutputCollapseAllInCellAction private constructor() : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = getCollapsingComponents(e).isNotEmpty()
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val collapsingComponents = getCollapsingComponents(e)
    return collapsingComponents.isNotEmpty() && collapsingComponents.all { it.size.get().collapsed }
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getCollapsingComponents(e).forEach {
      it.size.set(it.size.get().copy(collapsed = state))
    }
  }
}

internal class NotebookOutputCollapseSingleInCellAction private constructor() : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = getCollapsingComponents(e).isNotEmpty()
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    getExpectedComponent(e)?.size?.get()?.collapsed == true

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val component = getExpectedComponent(e)
    if (component != null) {
      component.size.set(component.size.get().copy(collapsed = state))
    }
  }

  private fun getExpectedComponent(e: AnActionEvent): EditorCellOutput? =
    e.dataContext.getData(NOTEBOOK_CELL_OUTPUT_DATA_KEY)
}

private fun getCollapsingComponents(e: AnActionEvent): List<EditorCellOutput> {
  val selectedCellIntervals = e.dataContext.selectedCellIntervals ?: return emptyList()
  val notebookEditor = e.dataContext.notebookEditor ?: return emptyList()

  return selectedCellIntervals.flatMap { getCollapsingComponents(notebookEditor, it) }
}

private fun getCollapsingComponents(editor: Editor, interval: NotebookCellLines.Interval): List<EditorCellOutput> =
  NotebookCellInlayManager.get(editor)
    ?.cells
    ?.get(interval.ordinal)
    ?.outputs
    ?.outputs
    ?.get()
  ?: emptyList()

private fun getCollapsingComponents(editor: Editor, intervals: Iterable<NotebookCellLines.Interval>): Sequence<EditorCellOutput> =
  intervals.asSequence()
    .filter { it.type == NotebookCellLines.CellType.CODE }
    .flatMap { getCollapsingComponents(editor, it) }

private val AnActionEvent.notebookCellInlayManager: NotebookCellInlayManager?
  get() = getData(PlatformDataKeys.EDITOR)?.let(NotebookCellInlayManager.Companion::get)

private val AnActionEvent.notebookEditor: EditorImpl?
  get() = notebookCellInlayManager?.editor