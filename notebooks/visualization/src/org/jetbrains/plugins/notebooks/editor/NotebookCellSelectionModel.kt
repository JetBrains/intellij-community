package org.jetbrains.plugins.notebooks.editor

interface NotebookCellSelectionModel {
  val primarySelectedCell: NotebookCellLines.Interval

  val selectedCells: List<NotebookCellLines.Interval>

  fun isSelectedCell(cell: NotebookCellLines.Interval): Boolean

  fun selectCell(cell: NotebookCellLines.Interval, makePrimary: Boolean = false)

  fun removeSecondarySelections()

  fun removeSelection(cell: NotebookCellLines.Interval)
}