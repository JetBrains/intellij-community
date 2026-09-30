package com.intellij.database.run.ui.columns

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridModel
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.GridColumnPinning
import org.jetbrains.annotations.ApiStatus
import java.util.function.IntUnaryOperator

/**
 * The rows of the column list, read from [grid].
 *
 * A pinned column comes first, then the rest in the sequence the grid shows them.
 * A nested column result keeps its tree, and a node comes before its children.
 *
 * [previous] is the order the list showed last. A hidden column has no place in the grid view, so it keeps
 * the place it held there, and a row does not move when the user clears its checkbox.
 */
@ApiStatus.Internal
fun buildColumnsListItems(grid: DataGrid, previous: List<ModelIndex<GridColumn>> = emptyList()): List<ColumnsListItem> {
  val model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
  val hierarchy = model as? HierarchicalColumnsDataGridModel
  val roots = hierarchy?.topLevelColumns
  if (roots != null) {
    val items = ArrayList<ColumnsListItem>()
    for (root in roots) addTree(grid, root, null, items)
    return items
  }
  return gridOrder(grid, model.columnIndices.asList(), previous).map { item(grid, it, null) }
}

private fun addTree(
  grid: DataGrid,
  column: HierarchicalGridColumn,
  parent: ColumnsListItem?,
  items: MutableList<ColumnsListItem>,
) {
  val children = column.children
  if (children.isEmpty()) {
    items.add(item(grid, ModelIndex.forColumn(grid, column.columnNumber), parent))
    return
  }
  val node = ColumnsListItem(modelIndex = null, name = column.name, parent = parent)
  items.add(node)
  for (child in children) addTree(grid, child, node, items)
}

private fun item(grid: DataGrid, columnIdx: ModelIndex<GridColumn>, parent: ColumnsListItem?): ColumnsListItem {
  val model: GridModel<GridRow, GridColumn> = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
  val column = model.getColumn(columnIdx)
  val helper = GridHelper.get(grid)
  return ColumnsListItem(
    modelIndex = columnIdx,
    name = grid.getUnambiguousColumnName(columnIdx),
    typeText = column?.let { helper.getColumnTypeText(grid, it) },
    parent = parent,
    traits = column?.let { helper.getColumnTraits(grid, it) } ?: emptySet(),
    visible = grid.isColumnEnabled(columnIdx),
    pinned = grid.isPinned(columnIdx),
    icon = column?.let { helper.getColumnIcon(grid, it, true) },
    canTogglePin = grid.canTogglePin(columnIdx),
  )
}

/**
 * The sequence the grid shows, with the pinned columns first.
 *
 * The frozen strip takes its order from the main table, so one view position orders both groups.
 */
private fun gridOrder(
  grid: DataGrid,
  columns: List<ModelIndex<GridColumn>>,
  previous: List<ModelIndex<GridColumn>>,
): List<ModelIndex<GridColumn>> {
  val toView = grid.rawIndexConverter.column2View()
  val pinned = columns.filter { grid.isPinned(it) }
  val rest = columns.filter { !grid.isPinned(it) }
  return inViewOrder(toView, pinned, previous) + inViewOrder(toView, rest, previous)
}

/**
 * Puts the shown columns of [group] in the sequence of the grid view, and leaves every hidden column where
 * it already sat.
 *
 * The places come from [previous], the order the list showed last, because a hidden column has no view
 * position of its own. Taking them from the data instead would move a hidden row as soon as the user had
 * reordered anything, since the data order and the view order then differ.
 */
private fun inViewOrder(
  toView: IntUnaryOperator,
  group: List<ModelIndex<GridColumn>>,
  previous: List<ModelIndex<GridColumn>>,
): List<ModelIndex<GridColumn>> {
  fun shown(column: ModelIndex<GridColumn>) = toView.applyAsInt(column.asInteger()) >= 0

  val places = previous.indices.associateBy { previous[it] }
  val slots = group.sortedBy { places[it] ?: (previous.size + group.indexOf(it)) }
  val shownColumns = group.filter { shown(it) }.sortedBy { toView.applyAsInt(it.asInteger()) }.iterator()
  return slots.map { if (shown(it)) shownColumns.next() else it }
}

private fun DataGrid.isPinned(column: ModelIndex<GridColumn>): Boolean =
  this is GridColumnPinning && isColumnPinned(column)

/**
 * Whether the pin control of [column] can act.
 *
 * An unpin always acts. A pin acts only while the columns that stay scrollable keep a usable width,
 * which is the same rule the Pin Columns action applies.
 */
private fun DataGrid.canTogglePin(column: ModelIndex<GridColumn>): Boolean =
  this is GridColumnPinning &&
  (isColumnPinned(column) || pinnedColumnsFit(ModelIndexSet.forColumns(this, column.asInteger())))
