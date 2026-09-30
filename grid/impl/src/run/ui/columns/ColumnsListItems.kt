package com.intellij.database.run.ui.columns

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridModel
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.run.actions.ColumnPinCommands
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.GridColumnPinning
import com.intellij.database.run.ui.TableResultPanel
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Builds rows from [grid], with pinned columns first.
 * The grid owns the order, including the positions of hidden columns.
 * A nested column result keeps its hierarchy, with each parent before its children.
 */
@ApiStatus.Internal
fun buildColumnsListItems(
  grid: DataGrid,
  presentation: (GridColumn) -> Pair<String?, Icon?> = {
    val helper = GridHelper.get(grid)
    helper.getColumnTypeText(grid, it) to helper.getColumnIcon(grid, it, true)
  },
): List<ColumnsListItem> {
  val model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
  val hierarchy = model as? HierarchicalColumnsDataGridModel
  val roots = hierarchy?.topLevelColumns
  val canTogglePin = ColumnPinCommands(grid).columnsThatCanTogglePin()
  if (roots != null) {
    val items = ArrayList<ColumnsListItem>()
    val pending = ArrayDeque<Pair<HierarchicalGridColumn, Int?>>()
    for (root in roots.asReversed()) pending.addLast(root to null)
    while (pending.isNotEmpty()) {
      val (column, parentIndex) = pending.removeLast()
      val depth = parentIndex?.let { items[it].depth + 1 } ?: 0
      if (column.children.isEmpty()) {
        items.add(item(grid, ModelIndex.forColumn(grid, column.columnNumber), parentIndex, depth, canTogglePin, presentation))
      }
      else {
        val index = items.size
        items.add(ColumnsListItem(modelIndex = null, name = column.name, parentIndex = parentIndex, depth = depth))
        for (child in column.children.asReversed()) pending.addLast(child to index)
      }
    }
    return items
  }
  return columnsInOrder(grid).map { item(grid, it, null, 0, canTogglePin, presentation) }
}

private fun item(
  grid: DataGrid,
  columnIdx: ModelIndex<GridColumn>,
  parentIndex: Int?,
  depth: Int,
  canTogglePin: Set<ModelIndex<GridColumn>>,
  presentation: (GridColumn) -> Pair<String?, Icon?>,
): ColumnsListItem {
  val model: GridModel<GridRow, GridColumn> = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
  val column = model.getColumn(columnIdx)
  val resolved = column?.let(presentation)
  return ColumnsListItem(
    modelIndex = columnIdx,
    name = grid.getUnambiguousColumnName(columnIdx),
    typeText = resolved?.first,
    parentIndex = parentIndex,
    visible = grid.isColumnEnabled(columnIdx),
    pinned = grid.isPinned(columnIdx),
    icon = resolved?.second,
    canTogglePin = columnIdx in canTogglePin,
    depth = depth,
  )
}

/**
 * The complete order of the grid, with the pinned columns first.
 *
 * The frozen strip takes its order from the main table, so the two groups keep the sequence they have here.
 */
@ApiStatus.Internal
fun columnsInOrder(grid: DataGrid): List<ModelIndex<GridColumn>> {
  val order = (grid as? TableResultPanel)?.columnsDisplayOrder
              ?: grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).columnIndices.asList()
  val (pinned, rest) = order.partition { grid.isPinned(it) }
  return pinned + rest
}

private fun DataGrid.isPinned(column: ModelIndex<GridColumn>): Boolean =
  this is GridColumnPinning && isColumnPinned(column)
