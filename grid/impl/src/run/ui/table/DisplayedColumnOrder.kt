package com.intellij.database.run.ui.table

import org.jetbrains.annotations.ApiStatus

/**
 * Immutable mapping from the two table column orders to the order shown to the user: frozen columns first, followed
 * by the remaining primary-table columns. All returned values are indices in the primary table's column model.
 */
@ApiStatus.Internal
class DisplayedColumnOrder(primaryModelOrder: List<Int>, frozenModelOrder: List<Int>) {
  private val frozenModelOrder = frozenModelOrder.toList()
  private val primaryIndexByModel = primaryModelOrder.withIndex().associate { it.value to it.index }

  val primaryViewIndices: List<Int> = buildList(primaryModelOrder.size) {
    for (modelIndex in frozenModelOrder) {
      primaryIndexByModel[modelIndex]?.let(::add)
    }
    val frozen = frozenModelOrder.toHashSet()
    for ((primaryIndex, modelIndex) in primaryModelOrder.withIndex()) {
      if (modelIndex !in frozen) add(primaryIndex)
    }
  }

  fun primaryIndexOfFrozen(frozenIndex: Int): Int? =
    frozenModelOrder.getOrNull(frozenIndex)?.let(primaryIndexByModel::get)

  fun adjacent(primaryIndex: Int, forward: Boolean): Int? {
    val position = primaryViewIndices.indexOf(primaryIndex)
    if (position < 0) return null
    return primaryViewIndices.getOrNull(position + if (forward) 1 else -1)
  }

  fun edge(first: Boolean): Int? = if (first) primaryViewIndices.firstOrNull() else primaryViewIndices.lastOrNull()

  fun range(anchor: Int, target: Int): List<Int>? {
    val anchorPosition = primaryViewIndices.indexOf(anchor)
    val targetPosition = primaryViewIndices.indexOf(target)
    if (anchorPosition < 0 || targetPosition < 0) return null
    return primaryViewIndices.subList(minOf(anchorPosition, targetPosition), maxOf(anchorPosition, targetPosition) + 1)
  }

  fun contains(primaryIndex: Int): Boolean = primaryIndex in primaryViewIndices
}
