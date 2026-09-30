package com.intellij.database.run.ui

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ResultView

/** A move of one column to the place before or after [target]. */
data class ColumnMove(
  val column: ModelIndex<GridColumn>,
  val target: ModelIndex<GridColumn>,
  val before: Boolean,
)

/**
 * A grid that keeps the complete column order, hidden columns included.
 *
 * A view asks the grid to apply the order after it rebuilds its columns.
 * It reports each move the user makes in its header.
 * The column list reads and changes the same order.
 */
interface ColumnOrderRestorer {
  /** The complete column order. Hidden columns keep their positions when the view changes. */
  val columnsDisplayOrder: List<ModelIndex<GridColumn>>

  /** Whether any column, including a hidden column, differs from the data order. */
  val isColumnsOrderModified: Boolean

  /** Applies the current order, pins, and widths to the view. */
  fun refreshColumnLayout()

  /** Restores the data order. The visibility, pins, and widths stay unchanged. */
  fun restoreNaturalColumnsOrder()

  /** Checks whether the current view permits a move of [column] beside [target]. */
  fun canMoveColumnInDisplayOrder(column: ModelIndex<GridColumn>, target: ModelIndex<GridColumn>): Boolean

  /** Moves [column] before or after [target] when permitted. All other columns keep their relative order. */
  fun moveColumnInDisplayOrder(column: ModelIndex<GridColumn>, target: ModelIndex<GridColumn>, before: Boolean)

  /**
   * Arranges the columns of [view] in the stored order.
   * Returns false when the grid leaves the arrangement to the view, which then places the column itself.
   */
  fun applyColumnsDisplayOrder(view: ResultView): Boolean

  /** Records [move], which the user made in the header of [view]. */
  fun columnMovedInView(view: ResultView, move: ColumnMove)
}
