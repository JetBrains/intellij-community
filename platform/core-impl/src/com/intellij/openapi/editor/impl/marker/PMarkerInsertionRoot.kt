// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.marker.PMarkerRelocationRoot.Insertions
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerDeltaProcessor
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRangeScalarUtil
import java.util.function.Consumer

/**
 * Buffers ordered insertions away from marker boundaries.
 * Overlapping policies must preserve the start and grow the end by the inserted length.
 * Queries translate offsets through the insertion log. Earlier roots retain their original ranges.
 * Boundary edits, replacements, moves, and marker mutations materialize the direct tree.
 * Once the insertion direction is established, a change of direction also materializes the tree.
 * [SnapshotMarkerRootStore.runInBatch] creates the buffer and completes its pending updates.
 */
class PMarkerInsertionRoot internal constructor(
  /** Stores marker ranges before the buffered insertions. */
  private val base: PMarkerDirectRoot,
  /** Records insertions in base offsets, or is null before the first buffered edit. */
  private val insertions: Insertions? = null,
  /** The start of the latest inserted fragment in this root's text. */
  private val lastStart: Int = 0,
  /** The exclusive end of the latest inserted fragment in this root's text. */
  private val lastEnd: Int = 0,
  /** Advances cached resolutions through the insertions represented by this root. */
  private val update: PMarkerResolutionUpdate? = null,
) : PMarkerRoot by base {
  override val resolutionCacheIdentity: Any = if (insertions == null) base.resolutionCacheIdentity else Any()

  /** Caches the direct tree without changing the ranges represented by this root. */
  @Volatile
  private var rebuilt: PMarkerDirectRoot? = null

  override fun advanceResolution(handle: SnapshotMarkerReference<*>, previousIdentity: Any, resolution: Long): Long =
    update?.advance(previousIdentity, resolution) ?: base.advanceResolution(handle, previousIdentity, resolution)

  override fun resolve(handle: SnapshotMarkerReference<*>): Long {
    val range = base.resolve(handle)
    val offsets = insertions
    if (offsets == null || !PMarkerResolution.isValid(range)) return range
    val start = PMarkerResolution.startOffset(range)
    val end = PMarkerResolution.endOffset(range)
    return TextRangeScalarUtil.toScalarRange(start + offsets.deltaAt(start), end + offsets.deltaAt(end))
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
    if (materialized != null || patch.endOffset() != start || patch.moveOffset() != start) {
      return (materialized ?: materialize()).applyPatch(
        patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer,
      )
    }
    validateMarkerPatch(patch, beforeText, afterText)
    val length = patch.newFragment().length
    if (length == 0) return this
    val descending = when {
      insertions == null -> false
      start < lastStart -> true
      start > lastEnd -> false
      else -> return materialize().applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer)
    }
    if (insertions != null && insertions.size > 1 && descending != insertions.descending) {
      return materialize().applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer)
    }
    val offsets = insertions?.withDirection(descending) ?: Insertions(descending)
    val baseStart = start - if (descending) 0 else offsets.totalDelta
    if (base.endsBefore(baseStart)) return this
    val affected = ArrayList<SnapshotMarkerReference<*>>()
    val canDefer = base.processRangeMarkersOverlappingWith(baseStart, baseStart, 0) { stored, delta ->
      val entry = shifted(stored, delta)
      val oldStart = entry.nodeStart
      val oldEnd = entry.nodeEnd
      if (start !in (oldStart + 1) until oldEnd || oldEnd > Int.MAX_VALUE - length) {
        return@processRangeMarkersOverlappingWith false
      }
      val result = entry.policy.transform(entry, patch, beforeText, afterText)
      if (!result.isValid || result.startOffset != oldStart || result.endOffset != oldEnd + length) {
        return@processRangeMarkersOverlappingWith false
      }
      affected.add(entry.handle)
      true
    }
    if (!canDefer) {
      return materialize().applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer)
    }
    val result = PMarkerInsertionRoot(
      base, offsets.append(baseStart, length), start, start + length,
      PMarkerResolutionUpdate(resolutionCacheIdentity, start, start, length, update),
    )
    for (handle in affected) affectedMarkerConsumer.accept(handle)
    return result
  }

  override fun getPrefixAggregate(offset: Int): Int = base.getPrefixAggregate(insertions?.toBaseOffset(offset) ?: offset)

  override fun processRangeMarkersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: MarkerDeltaProcessor,
  ): Boolean {
    val iterator = overlappingIterator(startOffset, endOffset, tastePreference)
    while (iterator.hasNext()) if (!processor.process(iterator.next(), 0)) return false
    return true
  }

  /** Finds candidates in base offsets, then checks them again in current offsets. */
  override fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<MarkerEntry> {
    require(startOffset in 0..endOffset) { "The query range is invalid" }
    val offsets = insertions ?: return base.overlappingIterator(startOffset, endOffset, tastePreference)
    val start = offsets.toBaseOffset(startOffset)
    val end = if (startOffset == endOffset) start else offsets.toBaseOffset(endOffset)
    return base.overlappingIterator(start, end, tastePreference).asSequence()
      .map { shifted(it, 0) }
      .filter { it.nodeStart <= endOffset && it.nodeEnd >= startOffset }
      .onEach { cacheResolution(it) }
      .iterator()
  }

  override fun processAllRangeMarkers(processor: MarkerDeltaProcessor): Boolean {
    if (insertions == null) return base.processAllRangeMarkers(processor)
    return base.processAllRangeMarkers { entry, delta ->
      val shifted = shifted(entry, delta)
      cacheResolution(shifted)
      processor.process(shifted, 0)
    }
  }

  private fun cacheResolution(entry: MarkerEntry) {
    entry.marker?.cacheResolution(this, TextRangeScalarUtil.toScalarRange(entry.nodeStart, entry.nodeEnd))
  }

  /** Applies the ancestor shift first, then translates both endpoints through the insertion log. */
  private fun shifted(entry: MarkerEntry, delta: Int): MarkerEntry {
    val start = entry.nodeStart + delta
    val end = entry.nodeEnd + delta
    return entry.copy(
      nodeStart = start + (insertions?.deltaAt(start) ?: 0),
      nodeEnd = end + (insertions?.deltaAt(end) ?: 0),
    )
  }

  /** Builds and caches a direct root with all buffered insertions applied. Earlier roots keep their ranges. */
  internal fun materialize(): PMarkerDirectRoot {
    if (insertions == null) return base
    rebuilt?.let { return it }
    return synchronized(this) {
      rebuilt ?: base.applyInsertions(insertions).also { rebuilt = it }
    }
  }

  override fun restoreEntries(entries: List<MarkerEntry>): PMarkerRoot =
    if (entries.isEmpty()) this else materialize().restoreEntries(entries)

  override fun insert(
    handle: SnapshotMarkerReference<*>,
    startOffset: Int,
    endOffset: Int,
    specMask: Byte,
    flavorFlags: Byte,
    measure: Int,
    policy: MarkerPolicy,
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
    if (PMarkerResolution.isAbsent(base.resolve(handle))) this else materialize().remove(handle)

  override fun purgeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerRoot =
    if (handles.isEmpty()) this else materialize().purgeMany(handles)

  override fun mergeValidMarkersFrom(other: PMarkerRoot): PMarkerRoot =
    if (other === this || other === base || other.isEmpty()) this else materialize().mergeValidMarkersFrom(other)
}
