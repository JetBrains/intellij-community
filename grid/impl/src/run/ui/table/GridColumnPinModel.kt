package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import java.util.Collections

/**
 * Immutable set of pinned model columns. Pinning changes where columns are rendered, not their order.
 */
class GridColumnPinModel private constructor(private val pinned: Set<ModelIndex<GridColumn>>) {
  constructor() : this(emptySet())

  fun isEmpty(): Boolean = pinned.isEmpty()

  fun isPinned(column: ModelIndex<GridColumn>): Boolean = column in pinned

  fun pinnedColumns(): Set<ModelIndex<GridColumn>> = Collections.unmodifiableSet(pinned)

  fun pin(column: ModelIndex<GridColumn>): GridColumnPinModel =
    if (column in pinned) this else GridColumnPinModel(pinned + column)

  fun pinAll(columns: Iterable<ModelIndex<GridColumn>>): GridColumnPinModel {
    val updated = pinned.toMutableSet()
    var changed = false
    for (column in columns) changed = updated.add(column) || changed
    return if (changed) GridColumnPinModel(updated) else this
  }

  fun unpin(column: ModelIndex<GridColumn>): GridColumnPinModel =
    if (column in pinned) GridColumnPinModel(pinned - column) else this

  fun unpinAll(columns: Iterable<ModelIndex<GridColumn>>): GridColumnPinModel {
    val updated = pinned.toMutableSet()
    var changed = false
    for (column in columns) changed = updated.remove(column) || changed
    return if (changed) GridColumnPinModel(updated) else this
  }

  fun unpinAll(): GridColumnPinModel = if (pinned.isEmpty()) this else GridColumnPinModel(emptySet())

  /**
   * Pins every column from the start of [displayOrder] up to and including [target], filling in any that are not pinned yet.
   * Returns this unchanged if the target is absent. [displayOrder] holds the visible model columns in the order the grid displays them.
   */
  fun pinUpToHere(target: ModelIndex<GridColumn>, displayOrder: List<ModelIndex<GridColumn>>): GridColumnPinModel {
    val position = displayOrder.indexOf(target)
    if (position < 0) return this
    return GridColumnPinModel(pinned + displayOrder.subList(0, position + 1))
  }

  /**
   * Offers pinning only for a visible, unpinned target, regardless of pins elsewhere in the prefix.
   * [displayOrder] holds the visible model columns in the order the grid displays them.
   */
  fun canPinUpToHere(target: ModelIndex<GridColumn>, displayOrder: List<ModelIndex<GridColumn>>): Boolean =
    target !in pinned && target in displayOrder

  override fun equals(other: Any?): Boolean = other is GridColumnPinModel && pinned == other.pinned

  override fun hashCode(): Int = pinned.hashCode()
}
