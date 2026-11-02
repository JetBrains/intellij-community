package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.ui.ColumnMove

/**
 * What [GridColumns] needs from the view that owns it: one request, to apply the grid's column order, and three notifications.
 * [GridColumns] holds no view, table or layout of its own.
 */
internal interface GridColumnsHost {
  /**
   * Asks the grid to put the columns in its display order, and returns whether it did. The grid does so only for its current, untransposed
   * view.
   */
  fun applyColumnsDisplayOrder(): Boolean

  /** A column moved in the view's column model. The grid records the move in its display order, except a move that it makes itself. */
  fun columnMoved(move: ColumnMove)

  /** [columns] are shown for the first time, so the column layout sizes them; they are grid rows in a transposed view. */
  fun columnsShown(columns: ModelIndexSet<*>)

  /** Columns are about to be removed, so a header that drags one lets go of it first (EA-59152, JDK-6586009). */
  fun columnsRemoving()
}
