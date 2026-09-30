package com.intellij.database.run.ui.columns

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridModel
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.GridColumnPinning
import com.intellij.database.run.ui.TableResultPanel
import org.jetbrains.annotations.ApiStatus

/**
 * The rows of the column list, read from [grid].
 *
 * A pinned column comes first, then the rest in the sequence the grid shows them.
 * A nested column result keeps its tree, and a node comes before its children.
 *
 * The grid owns the complete order, the hidden columns included, so the list is a projection of it and
 * keeps nothing of its own. A row therefore holds its place when the user clears its checkbox, and it
 * holds it again after the popup closes and opens.
 */
@ApiStatus.Internal
fun buildColumnsListItems(grid: DataGrid): List<ColumnsListItem> {
  val model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
  val hierarchy = model as? HierarchicalColumnsDataGridModel
  val roots = hierarchy?.topLevelColumns
  val canTogglePin = (grid as? GridColumnPinning)?.columnsThatCanTogglePin() ?: emptySet()
  if (roots != null) {
    val items = ArrayList<ColumnsListItem>()
    for (root in roots) addTree(grid, root, null, items, canTogglePin)
    return items
  }
  return columnsInOrder(grid).map { item(grid, it, null, canTogglePin) }
}

private fun addTree(
  grid: DataGrid,
  column: HierarchicalGridColumn,
  parent: ColumnsListItem?,
  items: MutableList<ColumnsListItem>,
  canTogglePin: Set<ModelIndex<GridColumn>>,
) {
  val children = column.children
  if (children.isEmpty()) {
    items.add(item(grid, ModelIndex.forColumn(grid, column.columnNumber), parent, canTogglePin))
    return
  }
  val node = ColumnsListItem(modelIndex = null, name = column.name, parent = parent)
  items.add(node)
  for (child in children) addTree(grid, child, node, items, canTogglePin)
}

private fun item(
  grid: DataGrid,
  columnIdx: ModelIndex<GridColumn>,
  parent: ColumnsListItem?,
  canTogglePin: Set<ModelIndex<GridColumn>>,
): ColumnsListItem {
  val model: GridModel<GridRow, GridColumn> = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
  val column = model.getColumn(columnIdx)
  val helper = GridHelper.get(grid)
  return ColumnsListItem(
    modelIndex = columnIdx,
    name = grid.getUnambiguousColumnName(columnIdx),
    typeText = column?.let { helper.getColumnTypeText(grid, it) },
    parent = parent,
    visible = grid.isColumnEnabled(columnIdx),
    pinned = grid.isPinned(columnIdx),
    icon = column?.let { helper.getColumnIcon(grid, it, true) },
    canTogglePin = columnIdx in canTogglePin,
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
