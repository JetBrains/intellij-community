// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.marker.MarkerPolicy
import com.intellij.openapi.editor.impl.marker.PMarkerResolution
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerDeltaProcessor
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.function.Consumer

/**
 * Combines the exact-range and line-range highlighter roots in one marker root.
 *
 * @property resolutionIdentity the compound cache token for the offsets and validity in both trees.
 */
@ApiStatus.Internal
open class CompoundPMarkerRoot private constructor(
  val exactRangeRoot: PMarkerRoot,
  val linesInRangeRoot: PMarkerRoot,
  private val resolutionIdentity: ResolutionIdentity = ResolutionIdentity(
    exactRangeRoot.resolutionCacheIdentity,
    linesInRangeRoot.resolutionCacheIdentity,
  ),
) : PMarkerRoot {
  override val resolutionCacheIdentity: Any
    get() = resolutionIdentity

  /** Advances offsets using the previous token for the target area's tree. */
  override fun advanceResolution(handle: SnapshotMarkerReference<*>, previousIdentity: Any, resolution: Long): Long {
    val previous = previousIdentity as? ResolutionIdentity ?: return PMarkerResolution.UNRESOLVED
    return when (targetArea(handle)) {
      HighlighterTargetArea.EXACT_RANGE -> advanceChildResolution(exactRangeRoot, handle, previous.exactRange, resolution)
      HighlighterTargetArea.LINES_IN_RANGE -> advanceChildResolution(linesInRangeRoot, handle, previous.linesInRange, resolution)
      null -> PMarkerResolution.UNRESOLVED
    }
  }

  /** Reuses [resolution] when the child token matches. Delegates to [root] when its token changes. */
  private fun advanceChildResolution(root: PMarkerRoot, handle: SnapshotMarkerReference<*>, previousIdentity: Any, resolution: Long): Long =
    if (previousIdentity === root.resolutionCacheIdentity) resolution else root.advanceResolution(handle, previousIdentity, resolution)

  /** Restores each entry in the child root where its handle is valid. */
  override fun restoreEntries(entries: List<MarkerEntry>): PMarkerRoot {
    return withRoots(exactRangeRoot.restoreEntries(entries.filter { exactRangeRoot.isValid(it.handle) }),
                     linesInRangeRoot.restoreEntries(entries.filter { linesInRangeRoot.isValid(it.handle) }))
  }

  override fun isEmpty(): Boolean = exactRangeRoot.isEmpty() && linesInRangeRoot.isEmpty()

  override fun isValid(handle: SnapshotMarkerReference<*>): Boolean =
    rootFor(handle)?.isValid(handle) ?: (exactRangeRoot.isValid(handle) || linesInRangeRoot.isValid(handle))

  override fun resolve(handle: SnapshotMarkerReference<*>): Long {
    rootFor(handle)?.let { return it.resolve(handle) }
    val exactResolution = exactRangeRoot.resolve(handle)
    if (!PMarkerResolution.isAbsent(exactResolution)) return exactResolution

    return linesInRangeRoot.resolve(handle)
  }

  @TestOnly
  override fun invalidationReason(handle: SnapshotMarkerReference<*>): String? {
    return if (PMarkerResolution.isAbsent(exactRangeRoot.resolve(handle))) {
      linesInRangeRoot.invalidationReason(handle)
    }
    else {
      exactRangeRoot.invalidationReason(handle)
    }
  }

  override fun applyPatch(
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
    affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
  ): PMarkerRoot {
    return withRoots(
      exactRangeRoot.applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer),
      linesInRangeRoot.applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer),
    )
  }

  override fun insert(
    handle: SnapshotMarkerReference<*>,
    startOffset: Int,
    endOffset: Int,
    specMask: Byte,
    flavorFlags: Byte,
    measure: Int,
    policy: MarkerPolicy,
  ): PMarkerRoot {
    val highlighter = handle.get() as? SnapshotRangeHighlighterImpl
    require(highlighter != null) { "A compound marker root requires a snapshot highlighter" }
    return if (highlighter.getTargetArea() == HighlighterTargetArea.EXACT_RANGE) {
      withRoots(
        exactRangeRoot.insert(handle, startOffset, endOffset, specMask, flavorFlags, measure, policy),
        linesInRangeRoot,
      )
    }
    else {
      withRoots(
        exactRangeRoot,
        linesInRangeRoot.insert(handle, startOffset, endOffset, specMask, flavorFlags, measure, policy),
      )
    }
  }

  /** Groups the entries by highlighter target area, then inserts each group into its child root. */
  override fun insertMany(entries: List<MarkerEntry>): PMarkerRoot {
    if (entries.isEmpty()) return this
    val exactEntries = ArrayList<MarkerEntry>()
    val lineEntries = ArrayList<MarkerEntry>()
    for (entry in entries) {
      val highlighter = entry.marker as? SnapshotRangeHighlighterImpl
      require(highlighter != null) { "A compound marker root requires a snapshot highlighter" }
      if (highlighter.getTargetArea() == HighlighterTargetArea.EXACT_RANGE) {
        exactEntries.add(entry)
      }
      else {
        lineEntries.add(entry)
      }
    }
    return withRoots(exactRangeRoot.insertMany(exactEntries), linesInRangeRoot.insertMany(lineEntries))
  }

  override fun updateFlavor(handle: SnapshotMarkerReference<*>, flavorFlags: Byte): PMarkerRoot {
    return updateMarker(handle) { it.updateFlavor(handle, flavorFlags) }
  }

  override fun updateSpec(handle: SnapshotMarkerReference<*>, specMask: Byte): PMarkerRoot {
    return updateMarker(handle) { it.updateSpec(handle, specMask) }
  }

  override fun updateMeasure(handle: SnapshotMarkerReference<*>, measure: Int): PMarkerRoot {
    return updateMarker(handle) { it.updateMeasure(handle, measure) }
  }

  override fun remove(handle: SnapshotMarkerReference<*>): PMarkerRoot {
    return updateMarker(handle) { it.remove(handle) }
  }

  override fun purgeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerRoot {
    return withRoots(exactRangeRoot.purgeMany(handles), linesInRangeRoot.purgeMany(handles))
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
    processor: MarkerDeltaProcessor,
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
    return exactRangeRoot.processAllRangeMarkers(processor) &&
       linesInRangeRoot.processAllRangeMarkers(processor)
  }

  @TestOnly
  fun withExactRangeRoot(root: PMarkerRoot): CompoundPMarkerRoot = withRoots(root, linesInRangeRoot)

  /** Returns the target area, or null when [handle] supplies no snapshot highlighter. */
  private fun targetArea(handle: SnapshotMarkerReference<*>): HighlighterTargetArea? =
    (handle.get() as? SnapshotRangeHighlighterImpl)?.getTargetArea()

  private fun rootFor(handle: SnapshotMarkerReference<*>): PMarkerRoot? =
    when (targetArea(handle)) {
      HighlighterTargetArea.EXACT_RANGE -> exactRangeRoot
      HighlighterTargetArea.LINES_IN_RANGE -> linesInRangeRoot
      null -> null
    }

  /**
   * Updates the child root for the marker's target area.
   * If the target area is unavailable, tries the exact-range root first and uses the line-range root only if the first root stays the same.
   */
  private inline fun updateMarker(handle: SnapshotMarkerReference<*>, update: (PMarkerRoot) -> PMarkerRoot): CompoundPMarkerRoot {
    when (targetArea(handle)) {
      HighlighterTargetArea.EXACT_RANGE -> return withRoots(update(exactRangeRoot), linesInRangeRoot)
      HighlighterTargetArea.LINES_IN_RANGE -> return withRoots(exactRangeRoot, update(linesInRangeRoot))
      null -> Unit
    }
    val updatedExactRoot = update(exactRangeRoot)
    if (updatedExactRoot !== exactRangeRoot) return withRoots(updatedExactRoot, linesInRangeRoot)
    return withRoots(exactRangeRoot, update(linesInRangeRoot))
  }

  /** Shares the compound cache token while both child tokens stay the same. */
  private fun withRoots(exactRangeRoot: PMarkerRoot, linesInRangeRoot: PMarkerRoot): CompoundPMarkerRoot {
    return if (exactRangeRoot === this.exactRangeRoot && linesInRangeRoot === this.linesInRangeRoot) {
      this
    }
    else {
      val exactIdentity = exactRangeRoot.resolutionCacheIdentity
      val linesIdentity = linesInRangeRoot.resolutionCacheIdentity
      val identity = if (exactIdentity === resolutionIdentity.exactRange && linesIdentity === resolutionIdentity.linesInRange) {
        resolutionIdentity
      }
      else ResolutionIdentity(exactIdentity, linesIdentity)
      CompoundPMarkerRoot(exactRangeRoot, linesInRangeRoot, identity)
    }
  }

  /**
   * Stores the tokens of both child roots.
   * A cached resolution can retain this identity after the roots are collected.
   *
   * @property exactRange the cache token of the exact-range tree.
   * @property linesInRange the cache token of the line-range tree.
   */
  private class ResolutionIdentity(val exactRange: Any, val linesInRange: Any)

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
    fun empty(): CompoundPMarkerRoot = CompoundPMarkerRoot(PMarkerRoot.empty(), PMarkerRoot.empty())

    private fun compareEntries(first: MarkerEntry, second: MarkerEntry): Int {
      val byStartOffset = first.nodeStart.compareTo(second.nodeStart)
      return if (byStartOffset != 0) byStartOffset else first.handle.compareTo(second.handle)
    }
  }
}
