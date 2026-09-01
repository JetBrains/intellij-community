// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.ex.MarkupIterator
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ConcurrentLongObjectMap
import com.intellij.util.containers.Java11Shim
import it.unimi.dsi.fastutil.longs.LongArrayList
import java.lang.ref.WeakReference
import java.util.Comparator
import java.util.NoSuchElementException
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.LongConsumer

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
  /**
   * Maps each document snapshot to this model's two persistent highlighter roots.
   * The map uses snapshot identity and weak keys. It keeps each root value strongly while its key is live.
   */
  private val highlighterRoots: ConcurrentMap<DocumentSnapshot, HighlighterRoots> = CollectionFactory.createConcurrentWeakIdentityMap()

  /** Maps each marker ID to the snapshot highlighter that this storage owns. */
  private val highlightersById: ConcurrentLongObjectMap<SnapshotRangeHighlighterImpl> = Java11Shim.createConcurrentLongObjectMap()

  /** A positive value prevents structural changes during iteration on the current thread. */
  private val iteratorDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

  /** A positive value prevents nested changes during removal notifications on the current thread. */
  private val removalDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

  /** Handles highlighters that become invalid after a document change. */
  private val documentListener: PrioritizedDocumentListener = object : PrioritizedDocumentListener {
    override fun getPriority(): Int = EditorDocumentPriorities.RANGE_MARKER

    override fun documentChanged(event: DocumentEvent) {
      highlightersChanged()
    }
  }

  /** Propagates this storage's roots when the snapshot engine derives or merges document snapshots. */
  private val rootStore: SnapshotMarkerRootStore = object : SnapshotMarkerRootStore {
    override fun containsSnapshot(snapshot: DocumentSnapshot): Boolean = highlighterRoots.containsKey(snapshot)

    override fun applyPatch(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot, patch: DocumentTextPatch) {
      applyHighlighterPatch(beforeSnapshot, afterSnapshot, patch)
    }

    override fun inherit(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot) {
      inheritHighlighterRoots(beforeSnapshot, afterSnapshot)
    }

    override fun merge(markerSnapshot: DocumentSnapshot, metadataSnapshot: DocumentSnapshot, mergedSnapshot: DocumentSnapshot) {
      mergeHighlighterRoots(markerSnapshot, metadataSnapshot, mergedSnapshot)
    }
  }

  init {
    SnapshotMarkerEngineImpl.registerRootStore(rootStore)
    document.addDocumentListener(documentListener)
  }

  fun dispose() {
    document.removeDocumentListener(documentListener)
    SnapshotMarkerEngineImpl.unregisterRootStore(rootStore)
    highlightersById.clear()
    highlighterRoots.clear()
  }

  fun add(highlighter: SnapshotRangeHighlighterImpl, startOffset: Int, endOffset: Int, spec: MarkerSpec) {
    assertMayChange()
    val rootReference = rootReference(currentSnapshot(), highlighter.targetAreaForStorage())
    val markerId = highlighter.idForStorage()
    val previous = highlightersById.putIfAbsent(markerId, highlighter)
    check(previous == null) { "Highlighter $markerId is already registered" }
    val markerReference = WeakReference<SnapshotRangeMarkerImpl>(highlighter)
    while (true) {
      val oldRoot = rootReference.get()
      val newRoot = oldRoot.insert(markerId, startOffset, endOffset, spec, highlighter.flavorFlags, markerReference)
      if (rootReference.compareAndSet(oldRoot, newRoot)) {
        model.invalidateHighlighterCache()
        return
      }
    }
  }

  fun rootReference(snapshot: DocumentSnapshot, targetArea: HighlighterTargetArea): AtomicReference<PMarkerRoot> {
    return highlighterRoots.computeIfAbsent(snapshot) { HighlighterRoots() }.rootReference(targetArea)
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  fun updateFlavor(highlighter: SnapshotRangeHighlighterImpl) {
    val rootReference = highlighter.currentRootReference()
    while (true) {
      val oldRoot = rootReference.get()
      val newRoot = oldRoot.updateFlavor(highlighter.idForStorage(), highlighter.flavorFlags)
      if (newRoot === oldRoot || rootReference.compareAndSet(oldRoot, newRoot)) return
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
    if (highlightersById.get(highlighter.idForStorage()) === highlighter) {
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
      if (highlightersById.remove(highlighter.idForStorage(), highlighter)) {
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
    val snapshot = currentSnapshot()
    val result = ArrayList<RangeHighlighterEx>()
    for (highlighter in highlightersById.values()) {
      if (highlighter.resolve(snapshot).isValid) result.add(highlighter)
    }
    result.sortWith(SNAPSHOT_HIGHLIGHTER_COMPARATOR)
    return result
  }

  fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Byte): MarkupIterator<RangeHighlighterEx> {
    val roots = highlighterRoots[currentSnapshot()] ?: return MarkupIterator.emptyIterator()
    val exact = markupIterator(roots.exactRoot.get(), startOffset, endOffset, tastePreference)
    val lineRange = MarkupModelImpl.roundToLineBoundaries(document, startOffset, endOffset)
    val lines = markupIterator(roots.lineRoot.get(), lineRange.startOffset, lineRange.endOffset, tastePreference)
    return MarkupIterator.mergeIterators(exact, lines, RangeHighlighterEx.BY_AFFECTED_START_OFFSET)
  }

  private fun highlightersChanged() {
    val roots = highlighterRoots[currentSnapshot()] ?: return
    for (markerId in roots.invalidatedMarkerIds) {
      val highlighter = highlightersById.get(markerId) ?: continue
      beforeRemoved(highlighter)
      afterRemoved(highlighter)
    }
  }

  private fun applyHighlighterPatch(
    beforeSnapshot: DocumentSnapshot,
    afterSnapshot: DocumentSnapshot,
    patch: DocumentTextPatch,
  ) {
    val beforeRoots = highlighterRoots[beforeSnapshot] ?: return
    val invalidatedMarkerIds = LongArrayList()
    val invalidatedMarkerConsumer = LongConsumer { markerId -> invalidatedMarkerIds.add(markerId) }
    val exactRoot = beforeRoots.exactRoot.get().applyPatch(
      patch, beforeSnapshot.text(), afterSnapshot.text(), invalidatedMarkerConsumer
    )
    val lineRoot = beforeRoots.lineRoot.get().applyPatch(
      patch, beforeSnapshot.text(), afterSnapshot.text(), invalidatedMarkerConsumer
    )
    val afterRoots = HighlighterRoots(exactRoot, lineRoot, invalidatedMarkerIds.toLongArray())
    highlighterRoots.putIfAbsent(afterSnapshot, afterRoots)
  }

  private fun inheritHighlighterRoots(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot) {
    val beforeRoots = highlighterRoots[beforeSnapshot] ?: return
    highlighterRoots.putIfAbsent(afterSnapshot, copyRoots(beforeRoots))
  }

  private fun mergeHighlighterRoots(
    markerSnapshot: DocumentSnapshot,
    metadataSnapshot: DocumentSnapshot,
    mergedSnapshot: DocumentSnapshot,
  ) {
    val markerRoots = highlighterRoots[markerSnapshot]
    val metadataRoots = highlighterRoots[metadataSnapshot]
    if (markerRoots == null && metadataRoots == null) return
    if (markerRoots == null) {
      highlighterRoots.putIfAbsent(mergedSnapshot, copyRoots(checkNotNull(metadataRoots)))
      return
    }
    if (metadataRoots == null) {
      highlighterRoots.putIfAbsent(mergedSnapshot, copyRoots(markerRoots))
      return
    }
    highlighterRoots.putIfAbsent(mergedSnapshot, HighlighterRoots(
      markerRoots.exactRoot.get().mergeValidMarkersFrom(metadataRoots.exactRoot.get()),
      markerRoots.lineRoot.get().mergeValidMarkersFrom(metadataRoots.lineRoot.get()),
    ))
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

  private fun copyRoots(roots: HighlighterRoots): HighlighterRoots {
    return HighlighterRoots(roots.exactRoot.get(), roots.lineRoot.get())
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
