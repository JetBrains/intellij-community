package com.intellij.database.run.ui.table

/**
 * Immutable pin state for the data grid: which columns are pinned, by model index. Pinning does not reorder anything —
 * the pinned columns are rendered in the frozen strip and keep their place in the column order (DBE-26267).
 */
class GridColumnPinModel private constructor(private val pinned: Set<Int>) {
  constructor() : this(emptySet())

  fun isEmpty(): Boolean = pinned.isEmpty()

  fun isPinned(columnId: Int): Boolean = columnId in pinned

  fun pinnedIds(): Set<Int> = pinned

  fun pin(columnId: Int): GridColumnPinModel = if (columnId in pinned) this else GridColumnPinModel(pinned + columnId)

  fun pinAll(columnIds: Collection<Int>): GridColumnPinModel {
    val updated = pinned.toMutableSet()
    var changed = false
    for (columnId in columnIds) changed = updated.add(columnId) || changed
    return if (changed) GridColumnPinModel(updated) else this
  }

  fun unpin(columnId: Int): GridColumnPinModel = if (columnId in pinned) GridColumnPinModel(pinned - columnId) else this

  fun unpinAll(columnIds: Collection<Int>): GridColumnPinModel {
    val updated = pinned.toMutableSet()
    var changed = false
    for (columnId in columnIds) changed = updated.remove(columnId) || changed
    return if (changed) GridColumnPinModel(updated) else this
  }

  fun unpinAll(): GridColumnPinModel = if (pinned.isEmpty()) this else GridColumnPinModel(emptySet())

  /**
   * Pins every column from the start of [displayOrder] up to and including [targetId], filling in any that are not
   * pinned yet. Returns this unchanged if the target is absent.
   */
  fun pinUpToHere(targetId: Int, displayOrder: List<Int>): GridColumnPinModel {
    val target = displayOrder.indexOf(targetId)
    if (target < 0) return this
    return GridColumnPinModel(pinned + displayOrder.subList(0, target + 1))
  }

  /** True when the prefix up to and including [targetId] has a column that is not pinned yet (so pinning would do something). */
  fun canPinUpToHere(targetId: Int, displayOrder: List<Int>): Boolean {
    val target = displayOrder.indexOf(targetId)
    return target >= 0 && displayOrder.subList(0, target + 1).any { it !in pinned }
  }

  override fun equals(other: Any?): Boolean = other is GridColumnPinModel && pinned == other.pinned

  override fun hashCode(): Int = pinned.hashCode()
}
