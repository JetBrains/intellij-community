package com.intellij.database.run.ui.table

import org.jetbrains.annotations.ApiStatus
import javax.swing.table.TableColumnModel

/**
 * The view's columns in the order the user sees them: the pinned ones first, then the rest, both in master order. It selects column ranges
 * in that order. It reads the master, which is the view's column model, and the pins, so it needs no table.
 *
 * A column is a master index. It stays `Int`: the slice passes master indices and holds no grid to type them.
 */
@ApiStatus.Internal
class DisplayedColumns(
  /** The view's column model, whose selection both tables share. */
  private val masterColumns: TableColumnModel,
  private val pins: ColumnPins,
) {
  /** The master's columns in displayed order. */
  fun order(): DisplayedColumnOrder<Int> = orderWith(pinnedColumns())

  /**
   * Changes the master's column selection by the columns from [anchor] to [target] in displayed order, as [change] says. The lead ends on
   * [target], the anchor on [anchor], or on [target] when [anchor] isn't a master column. False when [target] isn't one, or when nothing
   * is pinned: the displayed order is then the master's, which Swing's own ranges follow.
   */
  internal fun select(anchor: Int, target: Int, change: RangeChange): Boolean {
    val pinnedColumns = pinnedColumns()
    if (pinnedColumns.isEmpty()) return false
    val order = orderWith(pinnedColumns)
    val start = anchor.takeIf(order::contains) ?: target
    val range = order.range(start, target) ?: return false

    val selection = masterColumns.selectionModel
    // Preserve drag batching until release to prevent selection-driven viewport jumps.
    val wasAdjusting = selection.valueIsAdjusting
    selection.valueIsAdjusting = true
    try {
      if (change == RangeChange.REPLACE) selection.clearSelection()
      val runChange = if (change == RangeChange.REMOVE) RangeChange.REMOVE else RangeChange.ADD
      // Every table's slice hears of each change, so the range goes in as few intervals as it has runs of adjacent columns.
      for (run in adjacentRuns(range)) {
        runChange.applyTo(selection, run.first, run.last)
      }
      runChange.applyTo(selection, target, target)
      selection.anchorSelectionIndex = start
    }
    finally {
      selection.valueIsAdjusting = wasAdjusting
    }
    return true
  }

  private fun pinnedColumns(): List<Int> = (0 until masterColumns.columnCount).filter { pins.isPinned(masterColumns.getColumn(it)) }

  private fun orderWith(pinnedColumns: List<Int>): DisplayedColumnOrder<Int> =
    DisplayedColumnOrder(gridOrder = (0 until masterColumns.columnCount).toList(), pinnedOrder = pinnedColumns)
}

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

/** [columns] as runs of adjacent master indices, in the order the runs start. */
internal fun adjacentRuns(columns: List<Int>): List<IntRange> {
  val runs = mutableListOf<IntRange>()
  for (column in columns) {
    val last = runs.lastOrNull()
    if (last != null && column == last.last + 1) runs[runs.lastIndex] = last.first..column
    else runs.add(column..column)
  }
  return runs
}
