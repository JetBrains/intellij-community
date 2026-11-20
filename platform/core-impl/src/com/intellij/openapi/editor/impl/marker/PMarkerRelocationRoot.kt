// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerDeltaProcessor
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

/** Buffers point moves and ordered insertions in immutable roots until a batch ends or an operation needs the complete tree. */
@ApiStatus.Internal
class PMarkerRelocationRoot private constructor(
  private val base: PMarkerDirectRoot,
  private val positions: PersistentLongMap<Position>,
  private val shift: Int,
  private val boundary: Int,
  private val lastBoundary: Int,
  private val update: PMarkerResolutionUpdate,
  private val insertions: Insertions? = null,
) : PMarkerRoot by base {
  override val resolutionCacheIdentity: Any = Any()

  @Volatile
  private var rebuilt: PMarkerDirectRoot? = null

  override fun advanceResolution(previousIdentity: Any, resolution: Long): Long = update.advance(previousIdentity, resolution)

  override fun isValid(handle: SnapshotMarkerReference<*>): Boolean =
    positions.getUnchecked(handle.id) != null || base.isValid(handle)

  override fun resolve(handle: SnapshotMarkerReference<*>): Long {
    val position = positions.getUnchecked(handle.id) ?: return resolveBase(handle)
    val offset = position.entry.nodeStart + (shift - position.shift)
    return TextRangeScalarUtil.toScalarRange(offset, offset)
  }

  private fun resolveBase(handle: SnapshotMarkerReference<*>): Long {
    val range = base.resolve(handle)
    if (insertions == null || !PMarkerResolution.isValid(range)) return range
    val offset = PMarkerResolution.startOffset(range)
    return TextRangeScalarUtil.shift(range, insertions.deltaAt(offset))
  }

  override fun applyPatch(
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
    affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
  ): PMarkerRoot {
    val materialized = rebuilt
    val start = patch.startOffset()
    val beforePositions = start < boundary + shift
    if (materialized != null || patch.endOffset() != start || patch.moveOffset() != start ||
        !beforePositions && start <= lastBoundary + shift || base.hasPersistentMarkers) {
      return (materialized ?: materialize()).applyPatch(
        patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer,
      )
    }
    if (base.canDeferPointInsertions) {
      return deferInsertion(patch, beforeText, afterText, beforePositions, invalidatedMarkerConsumer, affectedMarkerConsumer)
    }
    val next = base.applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer)
    val delta = patch.newFragment().length
    if (next === base && delta == 0) return this
    return PMarkerRelocationRoot(
      next, positions, shift + if (beforePositions) delta else 0, boundary, lastBoundary,
      PMarkerResolutionUpdate(resolutionCacheIdentity, start, start, delta, update),
    )
  }

  private fun deferInsertion(
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    descending: Boolean,
    invalidated: Consumer<in SnapshotMarkerReference<*>>,
    affected: Consumer<in SnapshotMarkerReference<*>>,
  ): PMarkerRoot {
    validateMarkerPatch(patch, beforeText, afterText)
    val length = patch.newFragment().length
    if (length == 0) return this
    if (insertions != null && descending != insertions.descending) {
      return materialize().applyPatch(patch, beforeText, afterText, invalidated, affected)
    }
    val start = patch.startOffset()
    val baseStart = start - (if (descending) 0 else insertions?.totalDelta ?: 0)
    val changes = ArrayList<MarkerEntry>()
    val canDefer = base.processRangeMarkersOverlappingWith(baseStart, baseStart, 0) { stored ->
      if (positions.getUnchecked(stored.handle.id) != null) return@processRangeMarkersOverlappingWith true
      val entry = stored.copy(nodeStart = start, nodeEnd = start)
      val result = entry.policy.transform(entry, patch, beforeText, afterText)
      if (!result.isValid || result.startOffset != result.endOffset) return@processRangeMarkersOverlappingWith false
      changes.add(entry.copy(nodeStart = result.startOffset, nodeEnd = result.endOffset))
      true
    }
    if (!canDefer) return materialize().applyPatch(patch, beforeText, afterText, invalidated, affected)
    val nextShift = shift + if (descending) length else 0
    var nextPositions = positions
    var first = minOf(boundary, start - nextShift)
    var last = maxOf(lastBoundary, start + length - nextShift)
    for (entry in changes) {
      nextPositions = nextPositions.put(entry.handle.id, Position(entry, nextShift))
      first = minOf(first, start - nextShift, entry.nodeStart - nextShift)
      last = maxOf(last, start - nextShift, entry.nodeEnd - nextShift)
      affected.accept(entry.handle)
    }
    return PMarkerRelocationRoot(
      base, nextPositions, nextShift, first, last,
      PMarkerResolutionUpdate(resolutionCacheIdentity, start, start, length, update),
      (insertions ?: Insertions(PersistentVector(5), descending, 0)).append(baseStart, length),
    )
  }

  private fun materialize(): PMarkerDirectRoot {
    rebuilt?.let { return it }
    return synchronized(this) {
      rebuilt ?: run {
        val entries = ArrayList<MarkerEntry>(positions.size())
        if (insertions == null) {
          positions.forEachEntry { _, position -> entries.add(position.entryAt(shift)) }
          base.restorePointEntries(entries)
        }
        else {
          base.processAllRangeMarkers { entry, delta ->
            val position = positions.getUnchecked(entry.handle.id)
            val originalOffset = entry.nodeStart + delta
            val offset = originalOffset + insertions.deltaAt(originalOffset)
            entries.add(position?.entryAt(shift) ?: entry.copy(nodeStart = offset, nodeEnd = offset))
            true
          }
          base.rebuildEntries(entries)
        }
      }.also { rebuilt = it }
    }
  }

  override fun restoreEntries(entries: List<MarkerEntry>): PMarkerRoot =
    if (entries.isEmpty()) this else materialize().restoreEntries(entries)

  override fun insert(
    handle: SnapshotMarkerReference<*>, startOffset: Int, endOffset: Int, specMask: Byte, flavorFlags: Byte, measure: Int, policy: MarkerPolicy,
  ): PMarkerRoot = materialize().insert(handle, startOffset, endOffset, specMask, flavorFlags, measure, policy)

  override fun insertMany(entries: List<MarkerEntry>): PMarkerRoot =
    if (entries.isEmpty()) this else materialize().insertMany(entries)

  override fun updateFlavor(handle: SnapshotMarkerReference<*>, flavorFlags: Byte): PMarkerRoot =
    if (isValid(handle)) materialize().updateFlavor(handle, flavorFlags) else this

  override fun updateSpec(handle: SnapshotMarkerReference<*>, specMask: Byte): PMarkerRoot =
    if (isValid(handle)) materialize().updateSpec(handle, specMask) else this

  override fun updateMeasure(handle: SnapshotMarkerReference<*>, measure: Int): PMarkerRoot =
    if (isValid(handle)) materialize().updateMeasure(handle, measure) else this

  override fun remove(handle: SnapshotMarkerReference<*>): PMarkerRoot =
    if (PMarkerResolution.isAbsent(resolve(handle))) this else materialize().remove(handle)

  override fun purgeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerRoot {
    if (handles.isEmpty()) return this
    val materialized = materialize()
    val result = materialized.purgeMany(handles)
    return if (result === materialized) this else result
  }

  override fun mergeValidMarkersFrom(other: PMarkerRoot): PMarkerRoot =
    if (other === this || other === base || other.isEmpty()) this else materialize().mergeValidMarkersFrom(finish(other))

  override fun getPrefixAggregate(offset: Int): Int = base.getPrefixAggregate(offset)

  override fun processRangeMarkersOverlappingWith(
    startOffset: Int, endOffset: Int, tastePreference: Int, processor: Processor<in MarkerEntry>,
  ): Boolean = materialize().processRangeMarkersOverlappingWith(startOffset, endOffset, tastePreference, processor)

  override fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<MarkerEntry> =
    materialize().overlappingIterator(startOffset, endOffset, tastePreference)

  override fun processAllRangeMarkers(processor: MarkerDeltaProcessor): Boolean = materialize().processAllRangeMarkers(processor)

  private class Position(val entry: MarkerEntry, val shift: Int) {
    fun entryAt(currentShift: Int): MarkerEntry {
      val offset = entry.nodeStart + (currentShift - shift)
      return entry.copy(nodeStart = offset, nodeEnd = offset)
    }
  }

  private class Insertion(val offset: Int, val totalDelta: Int)

  private class Insertions(val entries: PersistentVector<Insertion>, val descending: Boolean, val totalDelta: Int) {
    fun append(offset: Int, length: Int): Insertions =
      Insertions(entries.put(entries.size().toLong(), Insertion(offset, totalDelta + length)), descending, totalDelta + length)

    fun deltaAt(offset: Int): Int {
      var low = 0
      var high = entries.size()
      while (low < high) {
        val middle = (low + high) ushr 1
        val insertion = requireNotNull(entries.getUnchecked(middle.toLong()))
        if (if (descending) insertion.offset >= offset else insertion.offset < offset) low = middle + 1
        else high = middle
      }
      val delta = if (low == 0) 0 else requireNotNull(entries.getUnchecked(low - 1L)).totalDelta
      return if (descending) totalDelta - delta else delta
    }
  }

  companion object {
    /** Moves an existing point with measure zero. The entry must preserve its policy and marker flags. */
    @JvmStatic
    fun move(root: PMarkerRoot, entry: MarkerEntry, defer: Boolean = true): PMarkerRoot {
      require(entry.nodeStart >= 0 && entry.nodeStart == entry.nodeEnd && entry.measure == 0) { "A relocation requires a point marker" }
      val previous = root as? PMarkerRelocationRoot
      val reuse = defer && previous != null && previous.rebuilt == null
      val base = if (reuse) previous.base else finish(root) as? PMarkerDirectRoot
      if (base == null || entry.policy.isPersistent) return finish(root).restoreEntries(listOf(entry))
      val old = root.resolve(entry.handle)
      require(PMarkerResolution.isValid(old) && PMarkerResolution.startOffset(old) == PMarkerResolution.endOffset(old)) {
        "The relocated marker must be a valid point"
      }
      if (!defer) return base.restorePointEntries(listOf(entry))
      val shift = if (reuse) previous.shift else 0
      val positions = if (reuse) previous.positions else PersistentLongMap.empty()
      val baseOffset = PMarkerResolution.startOffset(if (reuse) previous.resolveBase(entry.handle) else base.resolve(entry.handle))
      val boundary = minOf(baseOffset, entry.nodeStart) - shift
      val lastBoundary = maxOf(baseOffset, entry.nodeStart) - shift
      return PMarkerRelocationRoot(
        base, positions.put(entry.handle.id, Position(entry, shift)), shift,
        minOf(if (reuse) previous.boundary else Int.MAX_VALUE, boundary),
        maxOf(if (reuse) previous.lastBoundary else Int.MIN_VALUE, lastBoundary),
        PMarkerResolutionUpdate(
          root.resolutionCacheIdentity, minOf(PMarkerResolution.startOffset(old), entry.nodeStart),
          maxOf(PMarkerResolution.endOffset(old), entry.nodeEnd), 0, previous?.update,
        ),
        insertions = if (reuse) previous.insertions else null,
      )
    }

    /** Builds the complete tree when a caret batch ends. */
    @JvmStatic
    fun finish(root: PMarkerRoot): PMarkerRoot = if (root is PMarkerRelocationRoot) root.materialize() else root
  }
}
