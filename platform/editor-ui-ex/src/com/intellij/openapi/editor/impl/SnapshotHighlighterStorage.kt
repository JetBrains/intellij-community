// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.MarkupIterator
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.ConcurrentLongObjectMap
import com.intellij.util.containers.Java11Shim
import it.unimi.dsi.fastutil.longs.LongList
import org.jetbrains.annotations.TestOnly
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.Comparator
import java.util.NoSuchElementException
import java.util.concurrent.atomic.AtomicReference

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

  /** Receives registry references after their highlighters become unreachable. */
  private val highlighterQueue: ReferenceQueue<SnapshotRangeHighlighterImpl> = ReferenceQueue()

  /** Maps each marker ID to a weak reference used for notifications. The marker roots own valid highlighters. */
  private val highlightersById: ConcurrentLongObjectMap<HighlighterReference> = Java11Shim.createConcurrentLongObjectMap()

  /** A positive value prevents structural changes during iteration on the current thread. */
  private val iteratorDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

  /** A positive value prevents nested changes during removal notifications on the current thread. */
  private val removalDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

  private val rootStore = SnapshotMarkerRootStore(
    document,
    emptyRoot = CompoundPMarkerRoot.empty(),
    onMarkersInvalidated = ::highlightersChanged,
  )

  fun dispose() {
    rootStore.dispose()
    highlightersById.clear()
  }

  fun add(highlighter: SnapshotRangeHighlighterImpl, startOffset: Int, endOffset: Int, spec: MarkerSpec) {
    assertMayChange()
    processHighlighterQueue()
    val snapshot = currentSnapshot()
    val markerId = highlighter.idForStorage()
    val previous = highlightersById.putIfAbsent(markerId, HighlighterReference(highlighter, highlighterQueue))
    check(previous == null) { "Highlighter $markerId is already registered" }
    val markerReference = SnapshotMarkerEngineImpl.createMarkerReference(highlighter, retainStrong = true)
    rootStore.updateRoot(snapshot) {
      it.insert(markerId, startOffset, endOffset, spec, highlighter.flavorFlags, markerReference)
    }
    model.invalidateHighlighterCache()
  }

  fun rootReference(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> {
    return rootStore.rootReference(snapshot)
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  fun updateFlavor(highlighter: SnapshotRangeHighlighterImpl) {
    rootStore.updateRoot(currentSnapshot()) {
      it.updateFlavor(highlighter.idForStorage(), highlighter.flavorFlags)
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
    if (highlightersById.get(highlighter.idForStorage())?.get() === highlighter) {
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
    try {
      val markerId = highlighter.idForStorage()
      val reference = highlightersById.get(markerId)
      if (reference != null && reference.get() === highlighter && highlightersById.remove(markerId, reference)) {
        model.invalidateHighlighterCache()
        model.fireAfterRemoved(highlighter)
      }
    }
    finally {
      leaveRemoval()
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
    processHighlighterQueue()
    val snapshot = currentSnapshot()
    val result = ArrayList<RangeHighlighterEx>()
    for (reference in highlightersById.values()) {
      val highlighter = reference.get() ?: continue
      if (highlighter.resolve(snapshot).isValid) result.add(highlighter)
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

  private fun highlightersChanged(invalidatedMarkerIds: LongList) {
    processHighlighterQueue()
    val size = invalidatedMarkerIds.size
    for (i in 0 until size) {
      val markerId = invalidatedMarkerIds.getLong(i)
      val highlighter = highlightersById.get(markerId)?.get() ?: continue
      beforeRemoved(highlighter)
      afterRemoved(highlighter)
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

  private fun processHighlighterQueue() {
    while (true) {
      val reference = highlighterQueue.poll() as? HighlighterReference? ?: return
      highlightersById.remove(reference.markerId, reference)
    }
  }

  @TestOnly
  fun containsHighlighterId(markerId: Long): Boolean {
    processHighlighterQueue()
    return highlightersById.containsKey(markerId)
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
      val marker = entries.next().markerReference?.get()
      if (marker is SnapshotRangeHighlighterImpl) return marker
    }
    return null
  }

  private class HighlighterReference(
    highlighter: SnapshotRangeHighlighterImpl,
    queue: ReferenceQueue<SnapshotRangeHighlighterImpl>,
  ) : WeakReference<SnapshotRangeHighlighterImpl>(highlighter, queue) {
    val markerId: Long = highlighter.idForStorage()
  }

  companion object {
    private val SNAPSHOT_HIGHLIGHTER_COMPARATOR = Comparator<RangeHighlighterEx> { first, second ->
      val byStartOffset = first.affectedAreaStartOffset.compareTo(second.affectedAreaStartOffset)
      if (byStartOffset != 0) return@Comparator byStartOffset
      val byLayer = second.layer.compareTo(first.layer)
      if (byLayer != 0) byLayer else first.id.compareTo(second.id)
    }
  }
}
