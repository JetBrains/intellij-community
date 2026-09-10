package com.intellij.database.run.ui

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex

/** A result view that can render some of its columns in a frozen region, which stays put while the rest scroll. */
// Kotlin turns a read-only collection parameter into a Java wildcard, which a Java implementation cannot match.
@JvmSuppressWildcards
interface ResultViewWithFrozenColumns {
  /** Renders the visible columns of [pinnedColumns] in the frozen region. An empty set removes the region. */
  fun setFrozenColumns(pinnedColumns: Collection<ModelIndex<GridColumn>>)

  /** Whether freezing exactly [pinnedColumns] would leave the rest of the view usable. */
  fun canFitPinnedColumns(pinnedColumns: Set<ModelIndex<GridColumn>>): Boolean

  /** Scrolls the leftmost of [columns] into view and leaves the rows where they are. */
  fun scrollColumnsIntoView(columns: List<ModelIndex<GridColumn>>)
}
