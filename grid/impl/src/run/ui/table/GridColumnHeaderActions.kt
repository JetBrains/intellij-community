@file:JvmName("GridColumnHeaderActions")

package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn
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
      isColumnDisabledOrCollapsed(grid, column) || collapseManager.isColumnCollapsedSubtree(column)
    }
  }
}

/** Whether the grid hides [column], or a collapsed group hides it. */
private fun isColumnDisabledOrCollapsed(grid: DataGrid, column: HierarchicalGridColumn): Boolean =
  !isColumnEnabled(grid, column) || isSubtreeHiddenDueToCollapse(grid, column)

/** Whether the grid shows [column]. A group is shown while its first child is: hiding that child hides its siblings too. */
internal fun isColumnEnabled(grid: DataGrid, column: HierarchicalGridColumn): Boolean =
  grid.isColumnEnabled(modelIndexOf(grid, if (column.isLeaf) column else column.children.first()))

/** Whether a collapsed group hides [column]; a group counts by its first leaf. */
internal fun isSubtreeHiddenDueToCollapse(grid: DataGrid, column: HierarchicalGridColumn): Boolean {
  val collapseManager = grid.hierarchicalColumnsCollapseManager ?: return false
  return if (column.isLeaf) collapseManager.isColumnHiddenDueToCollapse(column)
  else collapseManager.isColumnHiddenDueToCollapse(column.leaves.firstOrNull())
}

private fun modelIndexOf(grid: DataGrid, column: GridColumn): ModelIndex<GridColumn> = ModelIndex.forColumn(grid, column.columnNumber)
