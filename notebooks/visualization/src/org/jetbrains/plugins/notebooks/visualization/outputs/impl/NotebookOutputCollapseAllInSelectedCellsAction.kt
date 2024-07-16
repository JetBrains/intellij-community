package org.jetbrains.plugins.notebooks.visualization.outputs.impl

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.notebooks.visualization.*
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCellOutput
import org.jetbrains.plugins.notebooks.visualization.ui.NOTEBOOK_CELL_OUTPUT_DATA_KEY

internal class NotebookOutputCollapseAllAction private constructor() : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.notebookEditor != null
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    allCollapsingComponents(e).all { it.collapsed }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    for (component in allCollapsingComponents(e)) {
      component.collapsed = state
    }
  }

  private fun allCollapsingComponents(e: AnActionEvent): Sequence<EditorCellOutput> {
    val inlayManager = e.notebookCellInlayManager ?: return emptySequence()
    return getCollapsingComponents(inlayManager.editor, NotebookCellLines.get(inlayManager.editor).intervals)
  }
}

// same as Collapse All Action, but collapse outputs of selected cells
internal class NotebookOutputCollapseAllInSelectedCellsAction private constructor() : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val editor = e.notebookEditor
    e.presentation.isEnabled = editor != null
    e.presentation.isVisible = editor?.cellSelectionModel?.let { it.selectedCells.size > 1 } ?: false
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    getSelectedCollapsingComponents(e).all { it.collapsed }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    for (component in getSelectedCollapsingComponents(e)) {
      component.collapsed = state
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
    return collapsingComponents.isNotEmpty() && collapsingComponents.all { it.collapsed }
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getCollapsingComponents(e).forEach {
      it.collapsed = state
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
    getExpectedComponent(e)?.collapsed ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getExpectedComponent(e)?.collapsed = state
  }

  private fun getExpectedComponent(e: AnActionEvent): EditorCellOutput? =
    e.dataContext.getData(NOTEBOOK_CELL_OUTPUT_DATA_KEY)
}

private fun getCollapsingComponents(e: AnActionEvent): List<EditorCellOutput> {
  return e.dataContext.getData(NOTEBOOK_CELL_LINES_INTERVAL_DATA_KEY)?.let { interval ->
    e.getData(PlatformDataKeys.EDITOR)?.let { getCollapsingComponents(it, interval) }
  } ?: emptyList()
}

private fun getCollapsingComponents(editor: Editor, interval: NotebookCellLines.Interval): List<EditorCellOutput> =
  NotebookCellInlayManager.get(editor)
    ?.cells?.get(interval.ordinal)
    ?.view
    ?.outputs
    ?.outputs
  ?: emptyList()

private fun getCollapsingComponents(editor: Editor, intervals: Iterable<NotebookCellLines.Interval>): Sequence<EditorCellOutput> =
  intervals.asSequence()
    .filter { it.type == NotebookCellLines.CellType.CODE }
    .flatMap { getCollapsingComponents(editor, it) }

private val AnActionEvent.notebookCellInlayManager: NotebookCellInlayManager?
  get() = getData(PlatformDataKeys.EDITOR)?.let(NotebookCellInlayManager.Companion::get)

private val AnActionEvent.notebookEditor: EditorImpl?
  get() = notebookCellInlayManager?.editor