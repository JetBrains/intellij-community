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
import com.intellij.util.Processor
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

  /**
   * Counts the active iterations on the current thread. A positive count prevents structural changes.
   * The counter stays in the thread after an iteration, so an iteration does not change the thread-local map.
   */
  private val iteratorDepth: ThreadLocal<IntArray> = ThreadLocal.withInitial { IntArray(1) }

  /** A positive value prevents nested changes during before-removal notifications on the current thread. */
  private val removalDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

  val rootStore: SnapshotMarkerRootStore<SnapshotRangeHighlighterImpl> = SnapshotMarkerRootStore(
    document, SnapshotRangeHighlighterImpl::class.java, retainStrong = true,
    onMarkersInvalidated = ::highlightersChanged, emptyRoot = CompoundPMarkerRoot.empty(),
  )

  fun dispose() {
    disposed = true
    rootStore.dispose(document.snapshotMarkerStores)
    model.invalidateHighlighterCache()
  }

  fun add(highlighter: SnapshotRangeHighlighterImpl, startOffset: Int, endOffset: Int, specMask: Byte, policy: MarkerPolicy) {
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
        it.insert(handle, startOffset, endOffset, specMask, flavorFlags, policy = policy)
      } != null
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
    if (iteratorDepth.get()[0] > 0) {
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

  fun processRangeHighlightersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    processor: Processor<in RangeHighlighterEx>,
  ): Boolean {
    val roots = rootStore.root(currentSnapshot()) as? CompoundPMarkerRoot ?: return true
    var start = startOffset.coerceIn(0, document.textLength)
    var end = endOffset.coerceIn(start, document.textLength)
    if (!roots.exactRangeRoot.isEmpty() && !roots.linesInRangeRoot.isEmpty()) return processMerged(roots, start, end, processor)
    val root = if (roots.linesInRangeRoot.isEmpty()) roots.exactRangeRoot
    else {
      val lineRange = MarkupModelImpl.roundToLineBoundaries(document, start, end)
      start = lineRange.startOffset
      end = lineRange.endOffset
      roots.linesInRangeRoot
    }
    iteratorDepth.get()[0]++
    try {
      return root.processRangeMarkersOverlappingWith(start, end, 0) { entry ->
        val marker = entry.marker
        marker !is SnapshotRangeHighlighterImpl || !marker.isValid || processor.process(marker)
      }
    }
    finally {
      iteratorDepth.get()[0]--
    }
  }

  /**
   * Processes both roots in the order of [overlappingIterator] without an iterator.
   * Collects the line highlighters first and processes each one before the first exact highlighter that does not start before it.
   */
  private fun processMerged(
    roots: CompoundPMarkerRoot,
    startOffset: Int,
    endOffset: Int,
    processor: Processor<in RangeHighlighterEx>,
  ): Boolean {
    val lineRange = MarkupModelImpl.roundToLineBoundaries(document, startOffset, endOffset)
    iteratorDepth.get()[0]++
    try {
      val lines = ArrayList<SnapshotRangeHighlighterImpl>()
      roots.linesInRangeRoot.processRangeMarkersOverlappingWith(lineRange.startOffset, lineRange.endOffset, 0) { entry ->
        val marker = entry.marker
        if (marker is SnapshotRangeHighlighterImpl && marker.isValid) lines.add(marker)
        true
      }
      var nextLine = 0
      var nextLineStart = if (lines.isEmpty()) Int.MAX_VALUE else lines[0].affectedAreaStartOffset
      val completed = roots.exactRangeRoot.processRangeMarkersOverlappingWith(startOffset, endOffset, 0) { entry ->
        val marker = entry.marker
        if (marker !is SnapshotRangeHighlighterImpl || !marker.isValid) return@processRangeMarkersOverlappingWith true
        val markerStart = marker.affectedAreaStartOffset
        while (nextLineStart <= markerStart) {
          if (!processor.process(lines[nextLine++])) return@processRangeMarkersOverlappingWith false
          nextLineStart = if (nextLine < lines.size) lines[nextLine].affectedAreaStartOffset else Int.MAX_VALUE
        }
        processor.process(marker)
      }
      if (!completed) return false
      while (nextLine < lines.size) {
        if (!processor.process(lines[nextLine++])) return false
      }
      return true
    }
    finally {
      iteratorDepth.get()[0]--
    }
  }

  private fun highlightersChanged(invalidatedHandles: List<SnapshotMarkerReference<SnapshotRangeHighlighterImpl>>) {
    val size = invalidatedHandles.size
    if (size == 0) return
    try {
      for (i in 0 until size) {
        val handle = invalidatedHandles[i]
        val highlighter = checkNotNull(handle.get())
        if (!highlighter.isRegistered) continue
        beforeRemoved(highlighter)
        afterRemoved(highlighter)
      }
    }
    finally {
      for (i in 0 until size) {
        val highlighter = checkNotNull(invalidatedHandles[i].get())
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
    iteratorDepth.get()[0]++
    return object : MarkupIterator<RangeHighlighterEx> {
      private var nextHighlighter: RangeHighlighterEx? = first
      private var disposed = false

      override fun dispose() {
        if (disposed) return
        disposed = true
        iteratorDepth.get()[0]--
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
