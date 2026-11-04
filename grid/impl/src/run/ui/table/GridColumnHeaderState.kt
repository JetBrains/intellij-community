@file:JvmName("GridColumnHeaderState")

package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.run.ui.DataAccessType

/** Sorts the grid by [column], or adds it to the sort; the scroll position stays. */
internal fun toggleSortOrder(grid: DataGrid, column: ModelIndex<GridColumn>, additive: Boolean) {
  grid.autoscrollLocker.runWithLock { grid.toggleSortColumns(listOf(column), additive) }
}

/**
 * Recomputes the depth of the grid's column hierarchy, which disabled columns and collapsed groups make shallower. Called after a group
 * collapses or expands, and after a hierarchical column hides.
 */
internal fun updateColumnHierarchyDepth(grid: DataGrid) {
  val hierarchicalReader = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).hierarchicalReader
  val collapseManager = grid.hierarchicalColumnsCollapseManager
  if (hierarchicalReader != null && collapseManager != null) {
    hierarchicalReader.updateDepthOfHierarchy { column ->
      GridTable.isColumnDisabledOrCollapsed(column, grid) || collapseManager.isColumnCollapsedSubtree(column)
    }
  }
}
