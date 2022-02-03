package org.jetbrains.plugins.notebooks.visualization

interface NotebookCellSelectionModel {
  val primarySelectedCell: NotebookCellLines.Interval

  val selectedCells: List<NotebookCellLines.Interval>

  val primarySelectedRegion: List<NotebookCellLines.Interval>

  val selectedRegions: List<List<NotebookCellLines.Interval>>

  fun isSelectedCell(cell: NotebookCellLines.Interval): Boolean

  fun selectCell(cell: NotebookCellLines.Interval, makePrimary: Boolean = false)

  fun removeSecondarySelections()

  fun removeSelection(cell: NotebookCellLines.Interval)

  fun selectSingleCell(cell: NotebookCellLines.Interval) {
    selectCell(cell, makePrimary = true)
    removeSecondarySelections()
  }
}