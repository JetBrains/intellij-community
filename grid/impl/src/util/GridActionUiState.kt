package com.intellij.database.util

import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.EDIT_MAXIMIZED_GRID_KEY
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.util.function.Predicate

/**
 * Stores the grid state captured on the EDT for background action updates.
 * The component references support identity checks without reading Swing state.
 */
@ApiStatus.Internal
class GridActionUiState private constructor(
  private val grid: DataGrid,
  private val leadCell: LeadCell?,
  private val cellComponentMatcher: Predicate<Component?>,
  val isValueTabSelected: Boolean,
) {
  fun isCellComponent(component: Component?): Boolean = cellComponentMatcher.test(component)

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

    @JvmStatic
    @RequiresEdt
    fun capture(grid: DataGrid): GridActionUiState {
      val view = grid.getUserData(EDIT_MAXIMIZED_GRID_KEY)
      return GridActionUiState(
        grid,
        captureLeadCell(grid),
        grid.resultView.captureCellComponentMatcher(),
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
