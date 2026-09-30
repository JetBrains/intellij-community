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
import com.intellij.database.run.ui.ColumnOrderRestorer
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.GridColumnPinning
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
  // The tree comes from the data model, because DATA_WITH_MUTATIONS wraps it in a GridMutationModel,
  // which is no HierarchicalColumnsDataGridModel and would leave every nested column flat.
  val hierarchy = grid.getDataModel(DataAccessType.DATABASE_DATA) as? HierarchicalColumnsDataGridModel
  val order = columnsInOrder(grid)
  val canTogglePin = ColumnPinCommands(grid).columnsThatCanTogglePin()
  if (hierarchy != null) {
    val items = ArrayList<ColumnsListItem>()
    val parents = HashMap<HierarchicalGridColumn, Int>()
    var pinnedGroup = true
    for (columnIdx in order) {
      if (pinnedGroup && !grid.isPinned(columnIdx)) {
        parents.clear()
        pinnedGroup = false
      }
      val column = hierarchy.getColumn(columnIdx) as? HierarchicalGridColumn
      var parentIndex: Int? = null
      for (parent in generateSequence(column?.parent) { it.parent }.toList().asReversed()) {
        val existing = parents[parent]
        if (existing != null) {
          parentIndex = existing
          continue
        }
        val depth = parentIndex?.let { items[it].depth + 1 } ?: 0
        val index = items.size
        items.add(ColumnsListItem(modelIndex = null, name = parent.name, parentIndex = parentIndex, depth = depth))
        parents[parent] = index
        parentIndex = index
      }
      val depth = parentIndex?.let { items[it].depth + 1 } ?: 0
      items.add(item(grid, columnIdx, parentIndex, depth, canTogglePin, presentation))
    }
    return items
  }
  return order.map { item(grid, it, null, 0, canTogglePin, presentation) }
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
  val order = (grid as? ColumnOrderRestorer)?.columnsDisplayOrder
              ?: grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).columnIndices.asList()
  val (pinned, rest) = order.partition { grid.isPinned(it) }
  return pinned + rest
}

private fun DataGrid.isPinned(column: ModelIndex<GridColumn>): Boolean =
  this is GridColumnPinning && isColumnPinned(column)
