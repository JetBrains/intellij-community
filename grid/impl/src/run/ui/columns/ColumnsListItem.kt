package com.intellij.database.run.ui.columns

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * One row of the column list.
 *
 * A nested column result builds a tree. A node of that tree holds no data of its own, so [modelIndex] is null
 * and [isColumn] is false. A node groups its children and stays in the list when a child matches the search.
 */
@ApiStatus.Internal
data class ColumnsListItem(
  val modelIndex: ModelIndex<GridColumn>?,
  val name: @NlsSafe String,
  val typeText: @NlsSafe String? = null,
  /** The parent row's index in the complete, unfiltered list. */
  val parentIndex: Int? = null,
  val visible: Boolean = true,
  val pinned: Boolean = false,
  val icon: Icon? = null,
  /** Whether the current view and available width permit the pin control to act. */
  val canTogglePin: Boolean = true,
  val depth: Int = 0,
) {
  /** True when the row stands for a column of the grid, and false when it stands for a node of a nested column tree. */
  val isColumn: Boolean get() = modelIndex != null

  override fun toString(): String = name
}
