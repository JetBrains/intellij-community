// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.marker.MarkerPolicy
import com.intellij.openapi.editor.impl.marker.PMarkerResolution
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerDeltaProcessor
import com.intellij.openapi.editor.impl.marker.PMarkerRootImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.util.Processor
import it.unimi.dsi.fastutil.longs.LongList
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.function.LongConsumer

/** Combines the exact-range and line-range highlighter roots in one marker root. */
@ApiStatus.Internal
open class CompoundPMarkerRoot private constructor(
  val exactRangeRoot: PMarkerRoot,
  val linesInRangeRoot: PMarkerRoot,
) : PMarkerRoot {
  override fun emptyRoot(): CompoundPMarkerRoot = empty()

  override fun isEmpty(): Boolean = exactRangeRoot.isEmpty() && linesInRangeRoot.isEmpty()

  override fun isValid(markerId: Long): Boolean = exactRangeRoot.isValid(markerId) || linesInRangeRoot.isValid(markerId)

  override fun resolve(markerId: Long): Long {
    val exactResolution = exactRangeRoot.resolve(markerId)
    if (!PMarkerResolution.isAbsent(exactResolution)) return exactResolution

    return linesInRangeRoot.resolve(markerId)
  }

  override fun invalidationReason(markerId: Long): String? {
    return if (PMarkerResolution.isAbsent(exactRangeRoot.resolve(markerId))) {
      linesInRangeRoot.invalidationReason(markerId)
    }
    else {
      exactRangeRoot.invalidationReason(markerId)
    }
  }

  override fun applyPatch(
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    invalidatedMarkerConsumer: LongConsumer,
    affectedMarkerConsumer: LongConsumer,
  ): PMarkerRoot {
    return withRoots(
      exactRangeRoot.applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer),
      linesInRangeRoot.applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer),
    )
  }

  override fun insert(
    markerId: Long,
    startOffset: Int,
    endOffset: Int,
    spec: Byte,
    flavorFlags: Byte,
    markerReference: SnapshotMarkerReference?,
    measure: Int,
    policy: MarkerPolicy,
  ): PMarkerRoot {
    val highlighter = markerReference?.get() as? SnapshotRangeHighlighterImpl
    require(highlighter != null) { "A compound marker root requires a snapshot highlighter" }
    return if (highlighter.targetAreaForStorage() == HighlighterTargetArea.EXACT_RANGE) {
      withRoots(
        exactRangeRoot.insert(markerId, startOffset, endOffset, spec, flavorFlags, markerReference, measure, policy),
        linesInRangeRoot,
      )
    }
    else {
      withRoots(
        exactRangeRoot,
        linesInRangeRoot.insert(markerId, startOffset, endOffset, spec, flavorFlags, markerReference, measure, policy),
      )
    }
  }

  override fun insertMany(entries: List<MarkerEntry>): PMarkerRoot {
    if (entries.isEmpty()) return this
    val exactEntries = ArrayList<MarkerEntry>()
    val lineEntries = ArrayList<MarkerEntry>()
    for (entry in entries) {
      val highlighter = entry.markerReference?.get() as? SnapshotRangeHighlighterImpl
      require(highlighter != null) { "A compound marker root requires a snapshot highlighter" }
      if (highlighter.targetAreaForStorage() == HighlighterTargetArea.EXACT_RANGE) {
        exactEntries.add(entry)
      }
      else {
        lineEntries.add(entry)
      }
    }
    return withRoots(exactRangeRoot.insertMany(exactEntries), linesInRangeRoot.insertMany(lineEntries))
  }

  override fun updateFlavor(markerId: Long, flavorFlags: Byte): PMarkerRoot {
    return updateMarker { it.updateFlavor(markerId, flavorFlags) }
  }

  override fun updateSpec(markerId: Long, spec: Byte): PMarkerRoot {
    return updateMarker { it.updateSpec(markerId, spec) }
  }

  override fun updateMeasure(markerId: Long, measure: Int): PMarkerRoot {
    return updateMarker { it.updateMeasure(markerId, measure) }
  }

  override fun remove(markerId: Long): PMarkerRoot {
    return updateMarker { it.remove(markerId) }
  }

  override fun removeMany(markerIds: LongList): PMarkerRoot {
    return withRoots(exactRangeRoot.removeMany(markerIds), linesInRangeRoot.removeMany(markerIds))
  }

  override fun purgeMany(markerIds: LongList): PMarkerRoot {
    return withRoots(exactRangeRoot.purgeMany(markerIds), linesInRangeRoot.purgeMany(markerIds))
  }

  override fun mergeValidMarkersFrom(other: PMarkerRoot): PMarkerRoot {
    require(other is CompoundPMarkerRoot) { "A compound marker root can merge only another compound marker root" }
    return withRoots(
      exactRangeRoot.mergeValidMarkersFrom(other.exactRangeRoot),
      linesInRangeRoot.mergeValidMarkersFrom(other.linesInRangeRoot),
    )
  }

  override fun processRangeMarkersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in MarkerEntry>,
  ): Boolean {
    if (!exactRangeRoot.processRangeMarkersOverlappingWith(startOffset, endOffset, tastePreference, processor)) return false
    return linesInRangeRoot.processRangeMarkersOverlappingWith(startOffset, endOffset, tastePreference, processor)
  }

  override fun getPrefixAggregate(offset: Int): Int {
    return exactRangeRoot.getPrefixAggregate(offset) + linesInRangeRoot.getPrefixAggregate(offset)
  }

  override fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<MarkerEntry> {
    return MergedMarkerIterator(
      exactRangeRoot.overlappingIterator(startOffset, endOffset, tastePreference),
      linesInRangeRoot.overlappingIterator(startOffset, endOffset, tastePreference),
    )
  }

  override fun processAllRangeMarkers(processor: MarkerDeltaProcessor): Boolean {
    throw AssertionError("Full traversal is not supported by CompoundPMarkerRoot")
  }

  @TestOnly
  fun withExactRangeRoot(root: PMarkerRoot): CompoundPMarkerRoot = withRoots(root, linesInRangeRoot)

  private inline fun updateMarker(update: (PMarkerRoot) -> PMarkerRoot): CompoundPMarkerRoot {
    val updatedExactRoot = update(exactRangeRoot)
    if (updatedExactRoot !== exactRangeRoot) return withRoots(updatedExactRoot, linesInRangeRoot)
    return withRoots(exactRangeRoot, update(linesInRangeRoot))
  }

  private fun withRoots(exactRangeRoot: PMarkerRoot, linesInRangeRoot: PMarkerRoot): CompoundPMarkerRoot {
    return if (exactRangeRoot === this.exactRangeRoot && linesInRangeRoot === this.linesInRangeRoot) {
      this
    }
    else {
      CompoundPMarkerRoot(exactRangeRoot, linesInRangeRoot)
    }
  }

  private class MergedMarkerIterator(
    private val firstIterator: Iterator<MarkerEntry>,
    private val secondIterator: Iterator<MarkerEntry>,
  ) : Iterator<MarkerEntry> {
    private var firstEntry: MarkerEntry? = nextOrNull(firstIterator)
    private var secondEntry: MarkerEntry? = nextOrNull(secondIterator)

    override fun hasNext(): Boolean = firstEntry != null || secondEntry != null

    override fun next(): MarkerEntry {
      val first = firstEntry
      val second = secondEntry
      if (first == null && second == null) throw NoSuchElementException()
      if (second == null || first != null && compareEntries(first, second) <= 0) {
        firstEntry = nextOrNull(firstIterator)
        return checkNotNull(first)
      }
      secondEntry = nextOrNull(secondIterator)
      return second
    }

    private fun nextOrNull(iterator: Iterator<MarkerEntry>): MarkerEntry? {
      return if (iterator.hasNext()) iterator.next() else null
    }
  }

  companion object {
    private object EMPTY: CompoundPMarkerRoot(PMarkerRootImpl.empty(), PMarkerRootImpl.empty()) {
      override fun toString(): String = "EMPTY"
    }

    fun empty(): CompoundPMarkerRoot = EMPTY

    private fun compareEntries(first: MarkerEntry, second: MarkerEntry): Int {
      val byStartOffset = first.nodeStart.compareTo(second.nodeStart)
      return if (byStartOffset != 0) byStartOffset else first.markerId.compareTo(second.markerId)
    }
  }
}
