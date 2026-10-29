package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.ui.ColumnMove

/**
 * Requests that [GridColumns] sends to the view that owns it. The view passes them on to the grid, which identifies a view's
 * column order by the view itself, and to its column layout. [GridColumns] holds no view, table or layout of its own.
 */
internal interface GridColumnsHost {
  /** Puts the columns in the grid's display order, and tells whether the grid did, which it does only for its current view. */
  fun applyColumnsDisplayOrder(): Boolean

  /** The user moved a column in a header, which the grid records in its display order. */
  fun columnMoved(move: ColumnMove)

  /** [columns] are shown for the first time, so the column layout sizes them; they are grid rows in a transposed view. */
  fun columnsShown(columns: ModelIndexSet<*>)

  /** Columns are about to be removed, so a header that drags one lets go of it first (EA-59152, JDK-6586009). */
  fun columnsRemoving()
}
