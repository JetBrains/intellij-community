package com.intellij.database.run.ui.table

import org.jetbrains.annotations.ApiStatus

/**
 * The order in which the user sees the grid's columns: the pinned ones first, in the given pinned order, then the rest in grid order.
 * [Column] identifies a grid column, such as its `ViewIndex<GridColumn>`. A pinned column that the grid order lacks is left out.
 */
@ApiStatus.Internal
class DisplayedColumnOrder<Column> private constructor(val displayedColumns: List<Column>) {
  constructor(gridOrder: List<Column>, pinnedOrder: List<Column>) : this(buildList(gridOrder.size) {
    val inGrid = gridOrder.toHashSet()
    pinnedOrder.filterTo(this) { it in inGrid }
    val pinned = pinnedOrder.toHashSet()
    gridOrder.filterTo(this) { it !in pinned }
  })

  /** The same order, with each column identified by [transform] of it. */
  fun <Other> map(transform: (Column) -> Other): DisplayedColumnOrder<Other> = DisplayedColumnOrder(displayedColumns.map(transform))

  fun adjacent(column: Column, forward: Boolean): Column? {
    val position = displayedColumns.indexOf(column)
    if (position < 0) return null
    return displayedColumns.getOrNull(position + if (forward) 1 else -1)
  }

  fun first(): Column? = displayedColumns.firstOrNull()

  fun last(): Column? = displayedColumns.lastOrNull()

  fun range(anchor: Column, target: Column): List<Column>? {
    val anchorPosition = displayedColumns.indexOf(anchor)
    val targetPosition = displayedColumns.indexOf(target)
    if (anchorPosition < 0 || targetPosition < 0) return null
    return displayedColumns.subList(minOf(anchorPosition, targetPosition), maxOf(anchorPosition, targetPosition) + 1)
  }

  fun contains(column: Column): Boolean = column in displayedColumns
}
