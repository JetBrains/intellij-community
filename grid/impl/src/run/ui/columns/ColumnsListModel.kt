package com.intellij.database.run.ui.columns

import org.jetbrains.annotations.ApiStatus

/** How the column list sorts its rows. */
@ApiStatus.Internal
enum class ColumnsListOrder {
  /** The sequence the grid shows. The caller supplies the items in this sequence. */
  GRID,

  /** The column names, sorted and flat. A node of a nested column tree drops out. */
  ALPHABETICAL,
}

/**
 * The rows of the column list.
 *
 * The model is immutable. A change to the search or to the order makes a new model.
 * [items] arrive in grid sequence, and a node of a nested column tree comes before its children.
 */
@ApiStatus.Internal
class ColumnsListModel(
  private val items: List<ColumnsListItem>,
  val filter: ColumnsListFilter = ColumnsListFilter(),
  val order: ColumnsListOrder = ColumnsListOrder.GRID,
) {
  fun withFilter(filter: ColumnsListFilter): ColumnsListModel = ColumnsListModel(items, filter, order)

  fun withOrder(order: ColumnsListOrder): ColumnsListModel = ColumnsListModel(items, filter, order)

  /**
   * The columns that the search keeps. A node of a nested column tree never counts, because it holds no data.
   * A bulk hide and a bulk show act on this list.
   */
  val matched: List<ColumnsListItem> = items.filter { it.isColumn && filter.matches(it) }

  /** The rows to show. In grid order a node stays when the search keeps one of its children. */
  val rows: List<ColumnsListItem> = buildRows()

  /** How many columns the grid shows. The search does not change this number. */
  val shownCount: Int = items.count { it.isColumn && it.visible }

  /** How many columns the grid has. The search does not change this number. */
  val totalCount: Int = items.count { it.isColumn }

  private fun buildRows(): List<ColumnsListItem> {
    if (order == ColumnsListOrder.ALPHABETICAL) {
      return matched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    val keep = HashSet<ColumnsListItem>()
    for (item in matched) {
      keep.add(item)
      for (ancestor in item.ancestors()) {
        if (!keep.add(ancestor)) break
      }
    }
    return items.filter { it in keep }
  }
}
