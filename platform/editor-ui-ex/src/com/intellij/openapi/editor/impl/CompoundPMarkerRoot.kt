// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.PMarkerResolution
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.editor.impl.marker.PMarkerRootImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.util.TextRange
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.NoSuchElementException
import java.util.function.LongConsumer

/** Combines the exact-range and line-range highlighter roots in one marker root. */
@ApiStatus.Internal
class CompoundPMarkerRoot private constructor(
  val exactRangeRoot: PMarkerRoot,
  val linesInRangeRoot: PMarkerRoot,
) : PMarkerRoot {
  override fun resolve(markerId: Long, absentRange: TextRange): PMarkerResolution {
    val exactResolution = exactRangeRoot.resolve(markerId, absentRange)
    if (exactResolution !is PMarkerResolution.Absent) return exactResolution

    val lineResolution = linesInRangeRoot.resolve(markerId, absentRange)
    if (lineResolution !is PMarkerResolution.Absent) return lineResolution

    return if (exactResolution.startOffset != absentRange.startOffset || exactResolution.endOffset != absentRange.endOffset) {
      exactResolution
    }
    else {
      lineResolution
    }
  }

  override fun applyPatch(
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    invalidatedMarkerConsumer: LongConsumer,
  ): PMarkerRoot {
    return withRoots(
      exactRangeRoot.applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer),
      linesInRangeRoot.applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer),
    )
  }

  override fun insert(
    markerId: Long,
    startOffset: Int,
    endOffset: Int,
    spec: MarkerSpec,
    flavorFlags: Byte,
    markerReference: SnapshotMarkerReference?,
    measure: Int,
  ): PMarkerRoot {
    val highlighter = markerReference?.get() as? SnapshotRangeHighlighterImpl
    require(highlighter != null) { "A compound marker root requires a snapshot highlighter" }
    return if (highlighter.targetAreaForStorage() == HighlighterTargetArea.EXACT_RANGE) {
      withRoots(
        exactRangeRoot.insert(markerId, startOffset, endOffset, spec, flavorFlags, markerReference, measure),
        linesInRangeRoot,
      )
    }
    else {
      withRoots(
        exactRangeRoot,
        linesInRangeRoot.insert(markerId, startOffset, endOffset, spec, flavorFlags, markerReference, measure),
      )
    }
  }

  override fun updateFlavor(markerId: Long, flavorFlags: Byte): PMarkerRoot {
    return updateMarker { it.updateFlavor(markerId, flavorFlags) }
  }

  override fun updateSpec(markerId: Long, spec: MarkerSpec): PMarkerRoot {
    return updateMarker { it.updateSpec(markerId, spec) }
  }

  override fun updateMeasure(markerId: Long, measure: Int): PMarkerRoot {
    return updateMarker { it.updateMeasure(markerId, measure) }
  }

  override fun remove(markerId: Long): PMarkerRoot {
    return updateMarker { it.remove(markerId) }
  }

  override fun purge(markerId: Long): PMarkerRoot {
    return updateMarker { it.purge(markerId) }
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
    private val EMPTY: CompoundPMarkerRoot = CompoundPMarkerRoot(PMarkerRootImpl.empty(), PMarkerRootImpl.empty())

    fun empty(): CompoundPMarkerRoot = EMPTY

    private fun compareEntries(first: MarkerEntry, second: MarkerEntry): Int {
      val byStartOffset = first.startOffset.compareTo(second.startOffset)
      return if (byStartOffset != 0) byStartOffset else first.markerId.compareTo(second.markerId)
    }
  }
}
