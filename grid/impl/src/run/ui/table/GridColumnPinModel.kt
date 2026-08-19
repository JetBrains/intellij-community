package com.intellij.database.run.ui.table

/**
 * Immutable pin state for the data grid: the set of pinned columns (by original/model index) and the derived
 * pinned-first display order. Pure logic with no UI or persistence, shared by every rendering approach (DBE-26267).
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

  /** Un-pins a column that is being hidden; un-hiding it later leaves it unpinned. */
  fun onColumnHidden(columnId: Int): GridColumnPinModel = unpin(columnId)

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

  /** Pinned columns first (keeping their relative order in [naturalOrder]), then the rest. */
  fun order(naturalOrder: List<Int>): List<Int> {
    if (pinned.isEmpty()) return naturalOrder.toList()
    val (pinnedColumns, rest) = naturalOrder.partition { it in pinned }
    return pinnedColumns + rest
  }

  override fun equals(other: Any?): Boolean = other is GridColumnPinModel && pinned == other.pinned

  override fun hashCode(): Int = pinned.hashCode()
}
