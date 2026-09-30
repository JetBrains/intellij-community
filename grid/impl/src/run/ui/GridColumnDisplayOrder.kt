package com.intellij.database.run.ui

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import org.jetbrains.annotations.ApiStatus

/**
 * The complete column order of a grid, hidden columns included.
 *
 * A view shows the visible columns in this order.
 * [com.intellij.database.run.ui.TableResultPanel] applies the order and checks which moves the view permits.
 */
@ApiStatus.Internal
class GridColumnDisplayOrder {
  /** Includes columns that are temporarily absent from the result. */
  private var order: List<ModelIndex<GridColumn>> = listOf()

  /**
   * Returns the stored order for the columns in [natural].
   * Keeps absent columns in the stored order and appends new columns.
   */
  fun reconcile(natural: List<ModelIndex<GridColumn>>): List<ModelIndex<GridColumn>> {
    val known = order.toSet()
    val arrived = natural.filter { it !in known }
    if (arrived.isNotEmpty()) order = order + arrived
    val current = natural.toSet()
    return if (order.size == current.size && current.containsAll(order)) order else order.filter { it in current }
  }

  /**
   * Replaces the order with [wanted], and appends whatever [natural] holds that [wanted] leaves out.
   * A caller therefore cannot lose a column by passing a partial order.
   */
  fun set(wanted: List<ModelIndex<GridColumn>>, natural: List<ModelIndex<GridColumn>>) {
    order = completedBy(wanted, natural)
  }

  /**
   * The order with [column] moved to the place before or after [target], or null when either is absent.
   * All other columns keep their relative order.
   */
  fun moved(
    natural: List<ModelIndex<GridColumn>>,
    column: ModelIndex<GridColumn>,
    target: ModelIndex<GridColumn>,
    before: Boolean,
  ): List<ModelIndex<GridColumn>>? {
    val moved = ArrayList(reconcile(natural))
    if (!moved.contains(target) || !moved.remove(column)) return null
    moved.add(moved.indexOf(target) + if (before) 0 else 1, column)
    return moved
  }

  /** Clears the stored order. The next read uses the data order. */
  fun reset() {
    order = listOf()
  }

  /** Takes [wanted] as the order without touching a view, which the caller has already arranged. */
  fun adopt(wanted: List<ModelIndex<GridColumn>>) {
    order = wanted.toList()
  }

  /**
   * Keeps the columns in [preferred] that occur in [natural], then appends the remaining columns.
   * Removes absent columns from the stored order.
   */
  private fun completedBy(
    preferred: List<ModelIndex<GridColumn>>,
    natural: List<ModelIndex<GridColumn>>,
  ): List<ModelIndex<GridColumn>> {
    val current = natural.toSet()
    val kept = preferred.filter { it in current }.distinct()
    val known = kept.toSet()
    return kept + natural.filter { it !in known }
  }
}
