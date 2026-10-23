package com.intellij.database.util

import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.EDIT_MAXIMIZED_GRID_KEY
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/** Stores the grid state captured on the EDT for background action updates. */
@ApiStatus.Internal
class GridActionUiState private constructor(
  private val grid: DataGrid,
  private val leadCell: LeadCell?,
  /** Whether the action acts on a cell: it runs in one of the grid's tables or in the floating toolbar of a cell, not on the grid panel,
   * another toolbar, or an open cell editor. */
  val isInCell: Boolean,
  val isValueTabSelected: Boolean,
) {
  fun getLeadSelectionCellValue(singleCellOnly: Boolean): Any? {
    val cell = leadCell ?: return null
    return if (singleCellOnly && !cell.isSingleCell) null else cell.value
  }

  private class LeadCell(val isSingleCell: Boolean, val value: Any?)

  companion object {
    /** Returns the snapshot only if it belongs to [grid], which may come from the file editor rather than the focused panel. */
    @JvmStatic
    fun getForGrid(grid: DataGrid, context: DataContext): GridActionUiState? =
      context.getData(DatabaseDataKeys.DATA_GRID_ACTION_UI_STATE_KEY)?.takeIf { it.grid === grid }

    /** Captures the state; [inCell] is true for the grid's tables and for the floating toolbar of a cell. */
    @JvmStatic
    @JvmOverloads
    @RequiresEdt
    fun capture(grid: DataGrid, inCell: Boolean = false): GridActionUiState {
      val view = grid.getUserData(EDIT_MAXIMIZED_GRID_KEY)
      return GridActionUiState(
        grid,
        captureLeadCell(grid),
        inCell,
        view != null && !view.isDisposed && view.isValueTabSelected(),
      )
    }

    private fun captureLeadCell(grid: DataGrid): LeadCell? {
      val selection = grid.selectionModel
      val row = selection.leadSelectionRow
      val column = selection.leadSelectionColumn
      if (row.asInteger() == -1 || column.asInteger() == -1) return null
      val singleCell = selection.selectedRowCount == 1 && selection.selectedColumnCount == 1
      val value = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(row, column)
      return LeadCell(singleCell, value)
    }
  }
}
