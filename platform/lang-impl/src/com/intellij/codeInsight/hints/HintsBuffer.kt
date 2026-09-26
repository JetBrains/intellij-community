// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.util.containers.ConcurrentIntObjectMap
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Utility class to accumulate hints. Thread-safe.
 */
@ApiStatus.Internal
class HintsBuffer {
  val inlineHints: ConcurrentIntObjectMap<MutableList<ConstrainedPresentation<*, HorizontalConstraints>>> = ConcurrentCollectionFactory.createConcurrentIntObjectMap<MutableList<ConstrainedPresentation<*, HorizontalConstraints>>>()
  internal val blockBelowHints: ConcurrentIntObjectMap<MutableList<ConstrainedPresentation<*, BlockConstraints>>> = ConcurrentCollectionFactory.createConcurrentIntObjectMap<MutableList<ConstrainedPresentation<*, BlockConstraints>>>()
  internal val blockAboveHints: ConcurrentIntObjectMap<MutableList<ConstrainedPresentation<*, BlockConstraints>>> = ConcurrentCollectionFactory.createConcurrentIntObjectMap<MutableList<ConstrainedPresentation<*, BlockConstraints>>>()

  /**
   * Counts all offsets of given [placement] which are not inside [other]
   */
  internal fun countDisjointElements(other: IntSet, placement: Inlay.Placement): Int {
    val map = getMap(placement)
    var count = 0
    val iterator = map.keys().iterator()
    while (iterator.hasNext()) {
      if (!other.contains(iterator.next())) {
        count++
      }
    }
    return count
  }

  internal fun contains(offset: Int, placement: Inlay.Placement): Boolean {
    return getMap(placement).containsKey(offset)
  }

  fun remove(offset: Int, placement: Inlay.Placement): MutableList<ConstrainedPresentation<*, *>>? {
    return getMap(placement).remove(offset)
  }

  @TestOnly
  fun clear() {
    inlineHints.clear()
    blockAboveHints.clear()
    blockBelowHints.clear()
  }

  @Suppress("UNCHECKED_CAST")
  private fun getMap(placement: Inlay.Placement) : ConcurrentIntObjectMap<MutableList<ConstrainedPresentation<*, *>>> {
    return when (placement) {
      Inlay.Placement.INLINE -> inlineHints
      Inlay.Placement.ABOVE_LINE -> blockAboveHints
      Inlay.Placement.BELOW_LINE -> blockBelowHints
      Inlay.Placement.AFTER_LINE_END -> TODO()
    } as ConcurrentIntObjectMap<MutableList<ConstrainedPresentation<*, *>>>
  }
}
