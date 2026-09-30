package com.intellij.database.run.ui.columns

import org.jetbrains.annotations.ApiStatus

/**
 * The rows of the column list.
 *
 * The model is immutable. A change to the search makes a new model.
 * [items] arrive in grid sequence, and a node of a nested column tree comes before its children.
 */
@ApiStatus.Internal
class ColumnsListModel(
  val items: List<ColumnsListItem>,
  val filter: ColumnsListFilter = ColumnsListFilter(),
) {
  fun withFilter(filter: ColumnsListFilter): ColumnsListModel = ColumnsListModel(items, filter)

  private val matchedIndices = items.indices.filter { items[it].isColumn && filter.matches(items[it]) }

  /** The matching columns. Bulk visibility actions use this list. */
  val matched: List<ColumnsListItem> = matchedIndices.map { items[it] }

  /** The rows to show, in grid order. A node stays when the search keeps one of its children. */
  val rows: List<ColumnsListItem> = buildRows()

  /** How many columns the grid shows. The search does not change this number. */
  val shownCount: Int = items.count { it.isColumn && it.visible }

  /** How many columns the grid has. The search does not change this number. */
  val totalCount: Int = items.count { it.isColumn }

  private fun buildRows(): List<ColumnsListItem> {
    val keep = BooleanArray(items.size)
    for (index in matchedIndices) {
      var current: Int? = index
      while (current != null && !keep[current]) {
        keep[current] = true
        current = items[current].parentIndex
      }
    }
    return items.filterIndexed { index, _ -> keep[index] }
  }
}
