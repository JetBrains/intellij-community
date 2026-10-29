package com.intellij.database.run.ui.table

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import java.util.Collections
import java.util.EventListener
import java.util.TreeMap

/**
 * The rows of the view that have a height of their own. Every table of the view applies them to itself, so a row has the same height in
 * both tables. That holds for a pinned table created after the resize too. Other rows use the uniform row height.
 *
 * A row is a row of the view's data model, as an `int`: a grid row, or a grid column in a transposed view. So neither
 * `ModelIndex<GridRow>` nor `ModelIndex<GridColumn>` would always be right. Heights are in pixels.
 */
internal class RowHeightModel {
  /** The height in pixels of each row that has one of its own, by model row. Sorted, so an insert or a delete shifts the rows after it. */
  private val heightByModelRow = TreeMap<Int, Int>()
  private val dispatcher = EventDispatcher.create(Listener::class.java)

  interface Listener : EventListener {
    /** [modelRow] got a new height of its own. */
    fun rowHeightChanged(modelRow: Int)

    /** No row has a height of its own anymore. */
    fun rowHeightsCleared()
  }

  fun addListener(listener: Listener, parentDisposable: Disposable) {
    dispatcher.addListener(listener, parentDisposable)
  }

  /** The height of [modelRow] in pixels, or null when the row uses the uniform row height. */
  fun heightOf(modelRow: Int): Int? = heightByModelRow[modelRow]

  /** The heights in pixels of the rows that have one of their own, by model row, for a table that applies all of them. Read-only. */
  fun heightsByModelRow(): Map<Int, Int> = Collections.unmodifiableMap(heightByModelRow)

  fun setHeight(modelRow: Int, heightInPixels: Int) {
    require(heightInPixels > 0) { "Row height must be positive: $heightInPixels" }
    if (heightByModelRow.put(modelRow, heightInPixels) != heightInPixels) dispatcher.multicaster.rowHeightChanged(modelRow)
  }

  fun clear() {
    if (heightByModelRow.isEmpty()) return
    heightByModelRow.clear()
    dispatcher.multicaster.rowHeightsCleared()
  }

  /** Moves the heights with the rows. The tables move the heights they applied themselves, so this fires no event. */
  fun rowsInserted(firstModelRow: Int, lastModelRow: Int) {
    shiftHeights(fromModelRow = firstModelRow, rowCount = lastModelRow - firstModelRow + 1)
  }

  /** Drops the heights of the deleted rows and moves the others, without an event as [rowsInserted]. */
  fun rowsDeleted(firstModelRow: Int, lastModelRow: Int) {
    heightByModelRow.subMap(firstModelRow, true, lastModelRow, true).clear()
    shiftHeights(fromModelRow = lastModelRow + 1, rowCount = firstModelRow - lastModelRow - 1)
  }

  /** Moves the height of each row from [fromModelRow] on by [rowCount] rows, down for a positive count and up for a negative one. */
  private fun shiftHeights(fromModelRow: Int, rowCount: Int) {
    val moved = heightByModelRow.tailMap(fromModelRow, true)
    if (moved.isEmpty()) return
    val shifted = moved.mapKeys { it.key + rowCount }
    moved.clear()
    heightByModelRow.putAll(shifted)
  }
}
