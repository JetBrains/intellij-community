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

/**
 * Buffers point moves and ordered insertions in immutable roots.
 * Each root resolves its positions independently of later edits.
 * Queries and unsupported operations can materialize the complete tree.
 * [runInBatch] materializes any remaining moves when the scope exits.
 */
@ApiStatus.Internal
class PMarkerRelocationRoot private constructor(
  /** Stores entries whose positions can be replaced by [positions]. */
  private val base: PMarkerDirectRoot,
  /** Maps moved handles to entries and the cumulative shift at their last move. */
  private val positions: PersistentLongMap<Position>,
  /** The cumulative shift shared by the entries in [positions]. */
  private val shift: Int,
  /** The lower bound of affected point positions, before adding [shift]. */
  private val boundary: Int,
  /** The upper bound of affected point positions, before adding [shift]. */
  private val lastBoundary: Int,
  /** Advances cached resolutions across the moves and edits represented by this root. */
  private val update: PMarkerResolutionUpdate,
  /** Records insertions still pending in [base], or is null when no insertion is deferred. */
  private val insertions: Insertions? = null,
) : PMarkerRoot by base {
  override val resolutionCacheIdentity: Any = Any()

  /** Caches the direct tree without changing the positions represented by this root. */
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
      (insertions ?: Insertions(descending)).append(baseStart, length),
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

  /** Stores a moved entry with the cumulative shift at the time of its move. */
  private class Position(val entry: MarkerEntry, val shift: Int) {
    /** Resolves the entry after later insertions change the cumulative shift. */
    fun entryAt(currentShift: Int): MarkerEntry {
      val offset = entry.nodeStart + (currentShift - shift)
      return entry.copy(nodeStart = offset, nodeEnd = offset)
    }
  }

  /** Stores a source offset, the cumulative inserted length, and this insertion's length. */
  private class Insertion(val offset: Int, val totalDelta: Int, val length: Int)

  /**
   * Stores ordered insertions in offsets from the base text.
   * The entries and cumulative lengths follow application order.
   * [deltaAt] supplies shifts for base offsets. [toBaseOffset] maps current offsets back.
   * The log shares entries without retaining marker roots.
   */
  internal class Insertions private constructor(
    /** The insertion records in application order. */
    private val entries: PersistentVector<Insertion>,
    /** Whether insertions proceed from larger base offsets to smaller offsets. */
    val descending: Boolean,
    /** The sum of all inserted lengths. */
    val totalDelta: Int,
  ) {
    constructor(descending: Boolean) : this(PersistentVector(5), descending, 0)

    val size: Int
      get() = entries.size()

    /** Selects the direction. A log with multiple entries must keep its current direction. */
    fun withDirection(descending: Boolean): Insertions {
      check(size <= 1 || this.descending == descending)
      return if (this.descending == descending) this else Insertions(entries, descending, totalDelta)
    }

    /** Appends an insertion in the selected order. [offset] refers to the base text. */
    fun append(offset: Int, length: Int): Insertions =
      Insertions(entries.put(entries.size().toLong(), Insertion(offset, totalDelta + length, length)), descending, totalDelta + length)

    /** Visits insertions in application order, with offsets in the text before each insertion. */
    fun forEach(action: (offset: Int, length: Int) -> Unit) {
      for (index in 0 until entries.size()) {
        val insertion = requireNotNull(entries.getUnchecked(index.toLong()))
        val offset = insertion.offset + if (descending) 0 else insertion.totalDelta - insertion.length
        action(offset, insertion.length)
      }
    }

    /** Maps a current offset to the base text. Offsets within inserted text map to that insertion's base offset. */
    fun toBaseOffset(offset: Int): Int {
      if (entries.size() == 0) return offset
      val first = requireNotNull(entries.getUnchecked(if (descending) entries.size() - 1L else 0L))
      if (offset < currentStart(first)) return offset
      val last = requireNotNull(entries.getUnchecked(if (descending) 0L else entries.size() - 1L))
      if (offset >= currentStart(last) + last.length) return offset - totalDelta
      var low = 0
      var high = entries.size()
      while (low < high) {
        val middle = (low + high) ushr 1
        val insertion = requireNotNull(entries.getUnchecked(middle.toLong()))
        val start = currentStart(insertion)
        if (if (descending) start > offset else start <= offset) low = middle + 1
        else high = middle
      }
      val index = if (descending) low else low - 1
      if (index !in 0 until entries.size()) return offset
      val insertion = requireNotNull(entries.getUnchecked(index.toLong()))
      return insertion.offset + maxOf(0, offset - currentStart(insertion) - insertion.length)
    }

    /** Returns the insertion's start in the text after all logged insertions. */
    private fun currentStart(insertion: Insertion): Int =
      insertion.offset + if (descending) totalDelta - insertion.totalDelta else insertion.totalDelta - insertion.length

    /** Returns the total length inserted strictly before [offset] in the base text. */
    fun deltaAt(offset: Int): Int {
      if (entries.size() == 0) return 0
      val first = requireNotNull(entries.getUnchecked(if (descending) entries.size() - 1L else 0L))
      if (offset <= first.offset) return 0
      val last = requireNotNull(entries.getUnchecked(if (descending) 0L else entries.size() - 1L))
      if (offset > last.offset) return totalDelta
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
    /**
     * Runs [runnable] and materializes any remaining relocation root in [rootStore] on exit, including when [runnable] throws.
     * The caller controls deferral through [move].
     */
    @JvmStatic
    fun runInBatch(rootStore: SnapshotMarkerRootStore<*>, runnable: Runnable) {
      try {
        runnable.run()
      }
      finally {
        val snapshot = rootStore.document?.core?.snapshot()
        if (snapshot != null && rootStore.root(snapshot) is PMarkerRelocationRoot) {
          rootStore.updateCurrentRoot { finish(it) }
        }
      }
    }

    /**
     * Moves a valid point marker with measure zero. [entry] must preserve its policy and marker flags.
     * Eligible deferred moves return a buffered root. Queries and unsupported operations can materialize it.
     * When [defer] is false, applies the move immediately.
     */
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

    private fun finish(root: PMarkerRoot): PMarkerRoot = if (root is PMarkerRelocationRoot) root.materialize() else root
  }
}
