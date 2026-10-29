package com.intellij.database.run.ui.table

import org.jetbrains.annotations.ApiStatus

/**
 * The order in which the user sees the grid's columns. The pinned columns come first, in the pinned table's order, then the other grid
 * columns in grid order. [Column] identifies a grid column; the pinned-table controller uses the column's `ViewIndex<GridColumn>`.
 */
@ApiStatus.Internal
class DisplayedColumnOrder<Column>(gridOrder: List<Column>, pinnedOrder: List<Column>) {
  val displayedColumns: List<Column> = buildList(gridOrder.size) {
    val inGrid = gridOrder.toHashSet()
    pinnedOrder.filterTo(this) { it in inGrid }
    val pinned = pinnedOrder.toHashSet()
    gridOrder.filterTo(this) { it !in pinned }
  }

  fun adjacent(column: Column, forward: Boolean): Column? {
    val position = displayedColumns.indexOf(column)
    if (position < 0) return null
    return displayedColumns.getOrNull(position + if (forward) 1 else -1)
  }

  fun edge(first: Boolean): Column? = if (first) displayedColumns.firstOrNull() else displayedColumns.lastOrNull()

  fun range(anchor: Column, target: Column): List<Column>? {
    val anchorPosition = displayedColumns.indexOf(anchor)
    val targetPosition = displayedColumns.indexOf(target)
    if (anchorPosition < 0 || targetPosition < 0) return null
    return displayedColumns.subList(minOf(anchorPosition, targetPosition), maxOf(anchorPosition, targetPosition) + 1)
  }

  fun contains(column: Column): Boolean = column in displayedColumns
}
