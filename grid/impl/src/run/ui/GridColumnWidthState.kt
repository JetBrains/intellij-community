package com.intellij.database.run.ui

import org.jetbrains.annotations.ApiStatus

/**
 * Column width state retained independently of a particular result-view instance.
 * A missing entry uses the initial width, zero represents a captured automatic width, and a positive value is user-set.
 */
@ApiStatus.Internal
class GridColumnWidthState<K> {
  private val capturedWidths = HashMap<K, Int>()

  fun record(key: K, width: Int, setByUser: Boolean) {
    capturedWidths[key] = if (setByUser && width > 0) width else 0
  }

  /** Returns the captured user width, zero for a captured automatic width, or [initialWidth] before first capture. */
  fun getWidth(key: K, initialWidth: Int): Int = capturedWidths[key] ?: initialWidth

  fun retain(keys: Collection<K>) {
    capturedWidths.keys.retainAll(keys.toHashSet())
  }
}
