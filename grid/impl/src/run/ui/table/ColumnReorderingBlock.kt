package com.intellij.database.run.ui.table

import java.util.concurrent.atomic.AtomicInteger

/**
 * Blocks column reordering in the headers of every table of a view while the grid writes a column move to its source. The view owns it,
 * and both tables' headers read it. Blocks nest: each [block] needs an [unblock].
 */
internal class ColumnReorderingBlock {
  /** How many column moves the grid is writing to its source. */
  private val pendingMoves = AtomicInteger(0)

  val isBlocked: Boolean
    get() = pendingMoves.get() > 0

  fun block() {
    pendingMoves.incrementAndGet()
  }

  fun unblock() {
    pendingMoves.decrementAndGet()
  }
}
