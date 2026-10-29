// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.MarkupIterator
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.marker.MarkerPolicy
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.TestOnly

/**
 * Stores and updates the snapshot highlighters for one markup model.
 */
internal class SnapshotHighlighterStorage(
  private val model: MarkupModelImpl,
  val document: DocumentImpl,
) {
  init {
    check(model.document === document)
  }

  @Volatile
  private var disposed: Boolean = false

  /** A positive value prevents structural changes during iteration on the current thread. */
  private val iteratorDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

  /** A positive value prevents nested changes during before-removal notifications on the current thread. */
  private val removalDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

  val rootStore: SnapshotMarkerRootStore<SnapshotRangeHighlighterImpl> = SnapshotMarkerRootStore.strong(
    document, SnapshotRangeHighlighterImpl::class.java,
    onMarkersInvalidated = ::highlightersChanged, emptyRoot = CompoundPMarkerRoot.empty(),
  )

  fun dispose() {
    disposed = true
    rootStore.dispose(document.snapshotMarkerStores)
    model.invalidateHighlighterCache()
  }

  fun add(highlighter: SnapshotRangeHighlighterImpl, startOffset: Int, endOffset: Int, spec: Byte, policy: MarkerPolicy) {
    assertMayChange()
    require(startOffset >= 0) { "startOffset must be non-negative" }
    require(endOffset >= startOffset) { "endOffset must not precede startOffset" }
    val handle = highlighter.markerHandle
    val flavorFlags = highlighter.flavorFlags
    highlighter.register()
    var inserted = false
    try {
      inserted = rootStore.updateCurrentRoot {
        require(endOffset <= document.textLength) { "Highlighter range exceeds document length" }
        it.insert(handle, startOffset, endOffset, spec, flavorFlags, policy = policy)
      }
    }
    finally {
      if (!inserted) highlighter.unregister()
      model.invalidateHighlighterCache()
    }
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  fun updateFlavor(highlighter: SnapshotRangeHighlighterImpl) {
    val flavorFlags = highlighter.flavorFlags
    rootStore.updateCurrentRoot {
      it.updateFlavor(highlighter.markerHandle, flavorFlags)
    }
  }

  fun fireAttributesChanged(
    highlighter: SnapshotRangeHighlighterImpl,
    renderersChanged: Boolean,
    fontStyleChanged: Boolean,
    foregroundColorChanged: Boolean,
  ) {
    model.fireAttributesChanged(highlighter, renderersChanged, fontStyleChanged, foregroundColorChanged)
  }

  fun beforeRemoved(highlighter: SnapshotRangeHighlighterImpl) {
    assertMayChange()
    if (!disposed && highlighter.isRegistered) {
      removalDepth.set(removalDepth.get() + 1)
      var completed = false
      try {
        model.fireBeforeRemoved(highlighter)
        completed = true
      }
      finally {
        if (!completed) leaveRemoval()
      }
    }
  }

  fun afterRemoved(highlighter: SnapshotRangeHighlighterImpl) {
    var removed = false
    try {
      if (highlighter.unregister() && !disposed) {
        model.invalidateHighlighterCache()
        removed = true
      }
    }
    finally {
      leaveRemoval()
    }
    if (removed) {
      model.fireAfterRemoved(highlighter)
    }
  }

  fun assertMayChange() {
    if (removalDepth.get() > 0) {
      throw IncorrectOperationException("Cannot change range highlighters during removal")
    }
    if (iteratorDepth.get() > 0) {
      throw IllegalStateException("Cannot change range highlighters during iteration")
    }
  }

  fun collectAll(): List<RangeHighlighterEx> {
    val root = rootStore.root(currentSnapshot()) ?: return emptyList()
    val result = ArrayList<RangeHighlighterEx>()
    root.processAllRangeMarkers { entry, _ ->
      val highlighter = entry.marker as? SnapshotRangeHighlighterImpl
      if (highlighter != null && highlighter.isRegistered && highlighter.isValid) result.add(highlighter)
      true
    }
    result.sortWith(SNAPSHOT_HIGHLIGHTER_COMPARATOR)
    return result
  }

  fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Byte): MarkupIterator<RangeHighlighterEx> {
    val snapshot = currentSnapshot()
    val roots = rootStore.root(snapshot) as? CompoundPMarkerRoot ?: return MarkupIterator.emptyIterator()
    val exact = markupIterator(roots.exactRangeRoot, startOffset, endOffset, tastePreference)
    val lineRange = MarkupModelImpl.roundToLineBoundaries(document, startOffset, endOffset)
    val lines = markupIterator(roots.linesInRangeRoot, lineRange.startOffset, lineRange.endOffset, tastePreference)
    return MarkupIterator.mergeIterators(exact, lines, RangeHighlighterEx.BY_AFFECTED_START_OFFSET)
  }

  private fun highlightersChanged(invalidatedHandles: List<SnapshotMarkerReference<SnapshotRangeHighlighterImpl>>) {
    val size = invalidatedHandles.size
    if (size == 0) return
    try {
      for (i in 0 until size) {
        val handle = invalidatedHandles[i]
        val highlighter = handle.get()
        if (!highlighter.isRegistered) continue
        beforeRemoved(highlighter)
        afterRemoved(highlighter)
      }
    }
    finally {
      for (i in 0 until size) {
        val highlighter = invalidatedHandles[i].get()
        highlighter.unregister()
      }
      model.invalidateHighlighterCache()
    }
  }

  private fun leaveRemoval() {
    val depth = removalDepth.get() - 1
    if (depth <= 0) {
      removalDepth.remove()
    }
    else {
      removalDepth.set(depth)
    }
  }

  @TestOnly
  fun containsHighlighterId(markerId: Long): Boolean {
    val root = rootStore.root(currentSnapshot()) ?: return false
    return !root.processAllRangeMarkers { entry, _ ->
      val highlighter = entry.marker as? SnapshotRangeHighlighterImpl
      highlighter == null || !highlighter.isRegistered || highlighter.id != markerId
    }
  }

  private fun markupIterator(
    root: PMarkerRoot,
    startOffset: Int,
    endOffset: Int,
    tastePreference: Byte,
  ): MarkupIterator<RangeHighlighterEx> {
    val entries = root.overlappingIterator(startOffset, endOffset, tastePreference.toInt())
    val first = nextSnapshotHighlighter(entries) ?: return MarkupIterator.emptyIterator()
    iteratorDepth.set(iteratorDepth.get() + 1)
    return object : MarkupIterator<RangeHighlighterEx> {
      private var nextHighlighter: RangeHighlighterEx? = first
      private var disposed = false

      override fun dispose() {
        if (disposed) return
        disposed = true
        val depth = iteratorDepth.get() - 1
        if (depth == 0) {
          iteratorDepth.remove()
        }
        else {
          iteratorDepth.set(depth)
        }
      }

      override fun peek(): RangeHighlighterEx {
        if (!hasNext()) throw NoSuchElementException()
        return checkNotNull(nextHighlighter)
      }

      override fun hasNext(): Boolean {
        if (nextHighlighter == null) nextHighlighter = nextSnapshotHighlighter(entries)
        return nextHighlighter != null
      }

      override fun next(): RangeHighlighterEx {
        val result = peek()
        nextHighlighter = null
        return result
      }
    }
  }

  private fun nextSnapshotHighlighter(entries: Iterator<PMarkerRoot.MarkerEntry>): SnapshotRangeHighlighterImpl? {
    while (entries.hasNext()) {
      val marker = entries.next().marker
      if (marker is SnapshotRangeHighlighterImpl && marker.isValid) return marker
    }
    return null
  }

  companion object {
    private val SNAPSHOT_HIGHLIGHTER_COMPARATOR: Comparator<RangeHighlighterEx> = Comparator { first, second ->
      val byStartOffset = first.affectedAreaStartOffset.compareTo(second.affectedAreaStartOffset)
      if (byStartOffset != 0) return@Comparator byStartOffset
      val byLayer = second.layer.compareTo(first.layer)
      if (byLayer != 0) byLayer else first.id.compareTo(second.id)
    }
  }
}
