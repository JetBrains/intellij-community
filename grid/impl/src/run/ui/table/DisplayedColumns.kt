package com.intellij.database.run.ui.table

import org.jetbrains.annotations.ApiStatus
import javax.swing.ListSelectionModel
import javax.swing.table.TableColumnModel

/**
 * How an extended range changes a selection. By JTable's rule, Shift sets the range; Ctrl+Shift adds it when the anchor cell is selected,
 * and removes it otherwise.
 */
@ApiStatus.Internal
enum class RangeChange {
  REPLACE, ADD, REMOVE;

  /** Applies the range from [anchor] to [lead] to [selection], which then has that anchor and lead, as JTable leaves them. */
  fun applyTo(selection: ListSelectionModel, anchor: Int, lead: Int) {
    when (this) {
      REPLACE -> selection.setSelectionInterval(anchor, lead)
      ADD -> selection.addSelectionInterval(anchor, lead)
      REMOVE -> selection.removeSelectionInterval(anchor, lead)
    }
  }

  companion object {
    /** The change an extend makes by the rule above; [toggle] says whether Ctrl is held. */
    @JvmStatic
    fun forExtend(toggle: Boolean, anchorSelected: Boolean): RangeChange = when {
      !toggle -> REPLACE
      anchorSelected -> ADD
      else -> REMOVE
    }
  }
}

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
