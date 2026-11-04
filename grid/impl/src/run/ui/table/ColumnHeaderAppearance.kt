package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.RowSortOrder
import com.intellij.database.run.ui.DataAccessType
import java.awt.Color
import kotlin.math.abs

/**
 * What the column headers of the view's tables show of the grid's state, so both headers show the same. A table reads it while it paints a
 * header cell. It holds the grid, as [ViewCellRenderers] does, since the state comes from five of the grid's sources. They are the sort,
 * the local filter, the hidden columns, the collapsed groups and the color model. A copy kept here would need a listener for each source,
 * and the color model is replaced without an event. It is read-only and answers with typed values, never with the grid or a grid service.
 */
internal class ColumnHeaderAppearance(private val grid: DataGrid) {
  /** The sort arrow of [column]'s header, or null when the grid can't sort by [column]. */
  fun sortMark(column: ModelIndex<GridColumn>): ColumnSortMark? {
    if (!GridHelper.get(grid).isSortingApplicable(column) || grid.getComparator(column) == null) return null
    val sortOrder = grid.getSortOrder(grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column))
    return ColumnSortMark.fromGridSortOrder(sortOrder, isNumbered = grid.countSortedColumns() > 1)
  }

  /** The filter icon of [column]'s header. */
  fun filterMark(column: ModelIndex<GridColumn>): ColumnFilterMark {
    val filterState = grid.localFilterState
    return when {
      !filterState.isEnabled -> ColumnFilterMark.NONE
      filterState.columnFilterEnabled(column) -> ColumnFilterMark.FILTERED
      else -> ColumnFilterMark.UNFILTERED
    }
  }

  /** Whether [column] is a collapsed group, whose header shows the group's lines only. */
  fun isCollapsedSubtree(column: HierarchicalGridColumn): Boolean =
    grid.hierarchicalColumnsCollapseManager?.isColumnCollapsedSubtree(column) == true

  /** Whether a collapsed group hides [column]; a group counts by its first leaf. */
  fun isHiddenByCollapse(column: HierarchicalGridColumn): Boolean = isSubtreeHiddenDueToCollapse(grid, column)

  /** Whether the grid shows [column] unless a collapsed group hides it; a group counts by its first child. */
  fun isEnabled(column: HierarchicalGridColumn): Boolean = isColumnEnabled(grid, column)

  /** The background of line [headerLine] of [column]'s header, or null for the table's background. */
  fun columnHeaderBackground(column: ModelIndex<GridColumn>, headerLine: Int): Color? =
    grid.colorModel.getColumnHeaderBackground(column, headerLine)

  /** The text color of [column]'s header. */
  fun columnHeaderForeground(column: ModelIndex<GridColumn>): Color = grid.colorModel.getColumnHeaderForeground(column)

  /** The background of the header cell of grid row [row], which a transposed table shows in its header; null for the table's background. */
  fun rowHeaderBackground(row: ModelIndex<GridRow>): Color? = grid.colorModel.getRowHeaderBackground(row)

  /** The text color of the header cell of grid row [row], which a transposed table shows in its header. */
  fun rowHeaderForeground(row: ModelIndex<GridRow>): Color = grid.colorModel.getRowHeaderForeground(row)
}

/**
 * The sort arrow of a column header: the column's sort [direction] and its [place] in the sort, 1-based, or null while it is unsorted. The
 * header shows the place while [isNumbered], that is, while more than one column is sorted.
 */
internal class ColumnSortMark(val direction: RowSortOrder.Type, val place: Int?, val isNumbered: Boolean) {
  companion object {
    /**
     * The mark of a column that the grid sorts by [sortOrder], in the grid's encoding: 0 unsorted, below 0 ascending, above 0 descending.
     * Its size is the column's place in the sort.
     */
    @JvmStatic
    fun fromGridSortOrder(sortOrder: Int, isNumbered: Boolean): ColumnSortMark {
      val direction = when {
        sortOrder < 0 -> RowSortOrder.Type.ASC
        sortOrder > 0 -> RowSortOrder.Type.DESC
        else -> RowSortOrder.Type.UNSORTED
      }
      return ColumnSortMark(direction, if (sortOrder == 0) null else abs(sortOrder), isNumbered)
    }
  }
}

/** The filter icon of a column header. */
internal enum class ColumnFilterMark {
  /** The local filter is off, so the header shows no filter icon. */
  NONE,

  /** The column has no filter of its own. */
  UNFILTERED,

  /** The column has a filter. */
  FILTERED,
}
