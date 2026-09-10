package com.intellij.database.run.ui

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import java.util.function.UnaryOperator

/** A grid that can pin columns, so that they stay in view while the rest of them scroll horizontally. */
interface GridColumnPinning {
  fun isColumnPinned(column: ModelIndex<GridColumn>): Boolean

  fun hasPinnedColumns(): Boolean

  fun setColumnsPinned(columns: ModelIndexSet<GridColumn>, pinned: Boolean)

  fun unpinAllColumns()

  /** Pins [columns] unless the result would leave no usable width for the unpinned columns. */
  fun pinColumns(columns: ModelIndexSet<GridColumn>)

  fun pinnedColumnsFit(columns: ModelIndexSet<GridColumn>): Boolean

  /** Pins every column up to and including [column], in the order the grid displays them. */
  fun pinColumnsUpToHere(column: ModelIndex<GridColumn>)

  fun canPinColumnsUpToHere(column: ModelIndex<GridColumn>): Boolean

  fun pinnedColumnsUpToHereFit(column: ModelIndex<GridColumn>): Boolean

  /**
   * Re-keys the pin state after a move in the data renumbered the columns, and lays the pinned columns out again.
   * [newToOld] maps a model column after the move to the one it was before.
   */
  fun restorePinnedColumnsAfterMoveInData(newToOld: UnaryOperator<ModelIndex<GridColumn>>)
}
