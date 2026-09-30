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
 * and [isColumn] is false. A node exists to group its children and to carry a name the search can match.
 */
@ApiStatus.Internal
class ColumnsListItem(
  val modelIndex: ModelIndex<GridColumn>?,
  val name: @NlsSafe String,
  val typeText: @NlsSafe String? = null,
  val parent: ColumnsListItem? = null,
  val visible: Boolean = true,
  val pinned: Boolean = false,
  val icon: Icon? = null,
  /** False when a pin would leave no usable width for the columns that stay scrollable. An unpin always acts. */
  val canTogglePin: Boolean = true,
) {
  /** True when the row stands for a column of the grid, and false when it stands for a node of a nested column tree. */
  val isColumn: Boolean get() = modelIndex != null

  /** How deep the row sits in a nested column tree. A top-level row has depth 0. */
  val depth: Int = if (parent == null) 0 else parent.depth + 1

  /** The nodes above this row, closest first. */
  fun ancestors(): Sequence<ColumnsListItem> = generateSequence(parent) { it.parent }

  override fun toString(): String = name
}
