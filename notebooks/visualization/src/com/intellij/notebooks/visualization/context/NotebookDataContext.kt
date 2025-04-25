package com.intellij.notebooks.visualization.context

import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.cellSelectionModel
import com.intellij.notebooks.visualization.ui.EditorCellOutput
import com.intellij.notebooks.visualization.ui.notebookEditor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.impl.EditorImpl

object NotebookDataContext {
  val NOTEBOOK_CELL_LINES_INTERVAL: DataKey<NotebookCellLines.Interval> = DataKey.create("NOTEBOOK_CELL_LINES_INTERVAL")

  val NOTEBOOK_CELL_OUTPUT_DATA_KEY: DataKey<EditorCellOutput> = DataKey.create("NOTEBOOK_CELL_OUTPUT")

  val DataContext.notebookEditor: EditorImpl?
    get() {
      val component = getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
      val editor = getData(PlatformCoreDataKeys.EDITOR)
      val noteEditor = NotebookDataContextUtils.getCurrentEditor(editor, component) ?: return null
      if (NotebookDataContextUtils.hasFocusedSearchReplaceComponent(noteEditor, component))
        return null
      return noteEditor
    }

  val DataContext.selectedCellIntervals: List<NotebookCellLines.Interval>?
    get() {
      val jupyterEditor = notebookEditor ?: return null
      val selectionModel = jupyterEditor.cellSelectionModel ?: return null
      return selectionModel.selectedCells
    }

  val DataContext.selectedCellInterval: NotebookCellLines.Interval?
    get() {
      val cached = getData(NOTEBOOK_CELL_LINES_INTERVAL)
      if (cached != null)
        return cached

      val editor = notebookEditor ?: return null
      val selectionModel = editor.cellSelectionModel ?: return null
      return selectionModel.primarySelectedCell
    }

  val DataContext.hoveredInterval: NotebookCellLines.Interval?
    get() {
      return notebookEditor?.notebookEditor?.hoveredCell?.get()?.interval
    }

  val DataContext.hoveredOrSelectedInterval: NotebookCellLines.Interval?
    get() = hoveredInterval ?: selectedCellInterval
}