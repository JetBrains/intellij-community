// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.util.Processor
import it.unimi.dsi.fastutil.longs.LongList
import java.util.function.LongConsumer

/**
 * Immutable persistent marker-state root.
 *
 * A root represents the complete marker state associated with one immutable document snapshot instance. A marker
 * store holds the atomic reference to the current root.
 *
 * None of the operations in this interface mutate the receiver. Operations
 * that change marker state return a new root that may structurally share data
 * with the receiver.
 */
interface PMarkerRoot {
  /** Returns an empty root with the same implementation and configuration as this root. */
  fun emptyRoot(): PMarkerRoot

  /**
   * Returns `true` when this root contains no valid markers.
   * Removed and invalid markers do not affect the result.
   */
  fun isEmpty(): Boolean

  /** Returns whether [markerId] is valid in this root without resolving its offsets. */
  fun isValid(markerId: Long): Boolean

  /**
   * Resolves [markerId] in this root.
   *
   * @return offsets and state packed by [PMarkerResolution], or [PMarkerResolution.ABSENT] when the marker is absent or removed
   */
  fun resolve(markerId: Long): Long

  /** Returns the invalidation reason, or `null` when the marker is valid or absent. */
  fun invalidationReason(markerId: Long): String?

  /**
   * Derives the marker root for the document state after [patch].
   *
   * A typical implementation:
   *
   * 1. Splits persistent endpoint indexes around the edited range.
   * 2. Shifts endpoints after the replaced range.
   * 3. Collapses endpoints covered by deleted text.
   * 4. Applies left and right greediness at edit boundaries.
   * 5. Applies marker-type-specific update rules.
   * 6. Returns a new persistent root.
   *
   * Already-invalid markers normally remain invalid.
   * [invalidatedMarkerConsumer] receives each marker ID that changes from valid to invalid.
   * [affectedMarkerConsumer] receives each marker ID that remains valid after its marker policy processes the patch.
   */
  fun applyPatch(
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    invalidatedMarkerConsumer: LongConsumer = EMPTY_LONG_CONSUMER,
    affectedMarkerConsumer: LongConsumer = EMPTY_LONG_CONSUMER,
  ): PMarkerRoot

  /**
   * Inserts a marker with a new ID. Restore existing markers at new offsets only after discarding their offset cache.
   *
   * The following range precondition must hold:
   *
   *     0 <= startOffset <= endOffset <= snapshot text length
   *
   * A duplicate [markerId] should cause [IllegalArgumentException] or a more
   * specific implementation-defined exception.
   * [spec] a marker flags, see [MarkerSpec]
   * [flavorFlags] is the value obtained from the marker's `RangeMarkerEx.getFlavorFlags()` method at insertion time.
   * [markerReference] provides the marker handle with either weak or strong ownership. Standalone roots may omit it.
   * [measure] contributes to prefix aggregate queries.
   * [policy] defines how document edits transform the marker.
   */
  fun insert(
    markerId: Long,
    startOffset: Int,
    endOffset: Int,
    spec: Byte,
    flavorFlags: Byte,
    markerReference: SnapshotMarkerReference? = null,
    measure: Int = 0,
    policy: MarkerPolicy = DefaultMarkerPolicy,
  ): PMarkerRoot

  /**
   * Inserts [entries] in list order and returns one new root.
   * The entry offsets are document offsets and must satisfy the preconditions of [insert].
   * Duplicate IDs cause an exception. An empty list returns the receiver unchanged.
   */
  fun insertMany(entries: List<MarkerEntry>): PMarkerRoot

  /**
   * Replaces the flavor flags of a valid marker, preserving its range and specification.
   *
   * If [markerId] is absent or invalid, this method returns the receiver unchanged.
   */
  fun updateFlavor(markerId: Long, flavorFlags: Byte): PMarkerRoot

  /**
   * Replaces the specification of a valid marker, preserving its range, flavor flags, handle reference, and policy.
   *
   * If [markerId] is absent or invalid, this method returns the receiver unchanged.
   */
  fun updateSpec(markerId: Long, spec: Byte): PMarkerRoot

  /**
   * Replaces the measure of a valid marker, preserving its range and other properties.
   *
   * If [markerId] is absent or invalid, this method returns the receiver unchanged.
   */
  fun updateMeasure(markerId: Long, measure: Int): PMarkerRoot

  /**
   * Removes the marker's endpoint anchors and secondary-index entries, retaining an absent tombstone without offsets.
   *
   * If [markerId] is absent, this method should return the receiver
   * unchanged.
   *
   * This is branch-local removal, not global marker disposal.
   */
  fun remove(markerId: Long): PMarkerRoot

  /**
   * Removes [markerIds] and retains absent tombstones, as [remove] does.
   * Repeated or absent IDs have no effect. An unchanged batch returns the receiver.
   */
  fun removeMany(markerIds: LongList): PMarkerRoot

  /**
   * Removes every trace of [markerIds], including their stored resolutions.
   *
   * This operation is intended for garbage-collected marker handles. Unlike [removeMany], it does not retain an absent
   * tombstone because no handle remains that could resolve against it.
   *
   * Repeated or absent IDs have no effect. An unchanged batch returns the receiver.
   */
  fun purgeMany(markerIds: LongList): PMarkerRoot

  /**
   * Keeps this root's marker states and adds valid markers that exist only in [other].
   */
  fun mergeValidMarkersFrom(other: PMarkerRoot): PMarkerRoot

  /**
   * Contains the immutable state of one valid marker.
   *
   * The tree maintains these invariants, where `ancestorDelta` is the sum of pending shifts in ancestor nodes:
   *
   *     nodeStart + ancestorDelta == resolve(markerId).startOffset
   *     nodeEnd + ancestorDelta == resolve(markerId).endOffset
   */
  data class MarkerEntry(
    /** Identifies the marker across roots and snapshots. */
    val markerId: Long,

    /** Gives the inclusive start stored in the tree node. */
    val nodeStart: Int,

    /** Gives the exclusive end stored in the tree node. */
    val nodeEnd: Int,

    /** Controls the marker boundaries at insertions. see [MarkerSpec]*/
    val spec: Byte,

    /** Selects query categories that the root uses for subtree pruning. */
    val flavorFlags: Byte,

    /** Holds the optional marker handle and its ownership mode. */
    val markerReference: SnapshotMarkerReference? = null,

    /** Contributes this integer to prefix aggregates. Non-empty markers must use the identity measure, zero. */
    val measure: Int = 0,

    /** Defines how document edits transform the marker. */
    val policy: MarkerPolicy = DefaultMarkerPolicy,
  ) {
    init {
      require(measure == 0 || nodeStart == nodeEnd) {
        "Only zero-length markers can have a non-zero measure"
      }
    }
  }

  /** Returns the prefix aggregate for valid markers whose start offset is not greater than [offset]. */
  fun getPrefixAggregate(offset: Int): Int

  /**
   * Processes valid markers that non-strictly intersect the requested range and contain every bit in [tastePreference].
   * A zero preference matches every marker.
   *
   * The processor receives the stored entry. Its node range can exclude a lazy shift from an ancestor node. Call [resolve]
   * when the processor needs actual offsets in the document snapshot.
   */
  fun processRangeMarkersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in MarkerEntry>,
  ): Boolean

  /**
   * Returns a lazy iterator over valid markers that non-strictly intersect `[startOffset, endOffset]` and match [tastePreference].
   * The iterator orders entries by start offset and marker ID.
   */
  fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<MarkerEntry>

  /**
   * Processes all valid markers by increasing actual start offset, then increasing marker ID.
   * The start offset includes pending shifts in this root.
   * The end offset, flavor flags, and tree shape do not affect the order.
   *
   * Supplies each entry and an offset delta. Add the delta to the node range to get the actual range.
   * Stops and returns `false` when [processor] returns `false`.
   */
  fun processAllRangeMarkers(processor: MarkerDeltaProcessor): Boolean

  /** Processes one marker entry with an offset delta. */
  fun interface MarkerDeltaProcessor {
    /** Processes an entry whose actual range is its node range plus [ancestorDelta]. */
    fun process(entry: MarkerEntry, ancestorDelta: Int): Boolean
  }

  companion object {
    /** Ignores invalidated marker IDs. */
    @JvmField
    val EMPTY_LONG_CONSUMER: LongConsumer = LongConsumer { }
  }
}
