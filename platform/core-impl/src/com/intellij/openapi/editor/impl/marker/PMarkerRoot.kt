// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.util.Processor
import java.util.function.Consumer

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
  /** Creates a handle that retains [marker] when [retainStrong] is true. */
  fun <T : SnapshotRangeMarkerImpl> createHandle(marker: T, retainStrong: Boolean): SnapshotMarkerReference<T?>

  /** Creates a handle that retains [marker]. */
  fun <T : SnapshotRangeMarkerImpl> createStrongHandle(marker: T): SnapshotMarkerReference<T>

  /** Identifies roots with the same marker offsets and validity. This token must not retain a root. */
  val resolutionCacheIdentity: Any

  /** Advances cached offsets across an edit, or requests a full resolution. */
  fun advanceResolution(previousIdentity: Any, resolution: Long): Long = PMarkerResolution.UNRESOLVED

  /** Replaces the offsets of existing valid markers and discards stale cached offsets. */
  fun restoreEntries(entries: List<MarkerEntry>): PMarkerRoot

  /** Returns an empty root with the same implementation and handle family as this root. */
  fun emptyRoot(): PMarkerRoot

  /**
   * Returns `true` when this root contains no valid markers.
   * Removed and invalid markers do not affect the result.
   */
  fun isEmpty(): Boolean

  /** Returns whether this root retains a valid, invalid, or removed state for [handle]. */
  fun containsMarker(handle: SnapshotMarkerReference<*>): Boolean

  /** Returns whether [handle] is valid in this root without resolving its offsets. */
  fun isValid(handle: SnapshotMarkerReference<*>): Boolean

  /**
   * Resolves [handle] in this root.
   *
   * @return offsets and state packed by [PMarkerResolution], or [PMarkerResolution.ABSENT] when the marker is absent or removed
   */
  fun resolve(handle: SnapshotMarkerReference<*>): Long

  /** Returns the invalidation reason, or `null` when the marker is valid or absent. */
  fun invalidationReason(handle: SnapshotMarkerReference<*>): String?

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
   * [invalidatedMarkerConsumer] receives each marker handle that changes from valid to invalid.
   * [affectedMarkerConsumer] receives each marker handle that remains valid after its marker policy processes the patch.
   */
  fun applyPatch(
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>> = EMPTY_HANDLE_CONSUMER,
    affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>> = EMPTY_HANDLE_CONSUMER,
  ): PMarkerRoot

  /**
   * Inserts a marker with a new handle. Use [restoreEntries] to change the offsets of existing markers.
   *
   * The following range precondition must hold:
   *
   *     0 <= startOffset <= endOffset <= snapshot text length
   *
   * A duplicate [handle] should cause [IllegalArgumentException] or a more
   * specific implementation-defined exception.
   * [spec] controls marker flags, see [MarkerSpec].
   * [flavorFlags] is the value obtained from the marker's `RangeMarkerEx.getFlavorFlags()` method at insertion time.
   * [measure] contributes to prefix aggregate queries.
   * [policy] defines how document edits transform the marker.
   */
  fun insert(
    handle: SnapshotMarkerReference<*>,
    startOffset: Int,
    endOffset: Int,
    spec: Byte,
    flavorFlags: Byte,
    measure: Int = 0,
    policy: MarkerPolicy = DefaultMarkerPolicy,
  ): PMarkerRoot

  /**
   * Inserts [entries] in list order and returns one new root.
   * The entry offsets are document offsets and must satisfy the preconditions of [insert].
   * Duplicate handles cause an exception. An empty list returns the receiver unchanged.
   */
  fun insertMany(entries: List<MarkerEntry>): PMarkerRoot

  /**
   * Replaces the flavor flags of a valid marker, preserving its range and specification.
   *
   * If [handle] is absent or invalid, this method returns the receiver unchanged.
   */
  fun updateFlavor(handle: SnapshotMarkerReference<*>, flavorFlags: Byte): PMarkerRoot

  /**
   * Replaces the specification of a valid marker, preserving its range, flavor flags, handle reference, and policy.
   *
   * If [handle] is absent or invalid, this method returns the receiver unchanged.
   */
  fun updateSpec(handle: SnapshotMarkerReference<*>, spec: Byte): PMarkerRoot

  /**
   * Replaces the measure of a valid marker, preserving its range and other properties.
   *
   * If [handle] is absent or invalid, this method returns the receiver unchanged.
   */
  fun updateMeasure(handle: SnapshotMarkerReference<*>, measure: Int): PMarkerRoot

  /**
   * Removes the marker's endpoint anchors and secondary-index entries, retaining an absent tombstone without offsets.
   *
   * If [handle] is absent, this method should return the receiver
   * unchanged.
   *
   * This is branch-local removal, not global marker disposal.
   */
  fun remove(handle: SnapshotMarkerReference<*>): PMarkerRoot

  /**
   * Removes [handles] and retains absent tombstones, as [remove] does.
   * Repeated or absent handles have no effect. An unchanged batch returns the receiver.
   */
  fun removeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerRoot

  /**
   * Removes every trace of [handles], including their stored resolutions.
   *
   * This operation is intended for garbage-collected marker handles. Unlike [removeMany], it does not retain an absent
   * tombstone because no handle remains that could resolve against it.
   *
   * Repeated or absent handles have no effect. An unchanged batch returns the receiver.
   */
  fun purgeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerRoot

  /**
   * Keeps this root's marker states and adds valid markers that exist only in [other].
   */
  fun mergeValidMarkersFrom(other: PMarkerRoot): PMarkerRoot

  /**
   * Contains the immutable state of one valid marker.
   *
   * The tree maintains these invariants, where `ancestorDelta` is the sum of pending shifts in ancestor nodes:
   *
   *     nodeStart + ancestorDelta == resolve(handle).startOffset
   *     nodeEnd + ancestorDelta == resolve(handle).endOffset
   */
  data class MarkerEntry(
    /** Identifies the marker across roots and snapshots. */
    val handle: SnapshotMarkerReference<*>,

    /** Gives the inclusive start stored in the tree node. */
    val nodeStart: Int,

    /** Gives the exclusive end stored in the tree node. */
    val nodeEnd: Int,

    /** Controls the marker boundaries at insertions. see [MarkerSpec]*/
    val spec: Byte,

    /** Selects query categories that the root uses for subtree pruning. */
    val flavorFlags: Byte,

    /** Contributes this integer to prefix aggregates. Non-empty markers must use the identity measure, zero. */
    val measure: Int = 0,

    /** Defines how document edits transform the marker. */
    val policy: MarkerPolicy = DefaultMarkerPolicy,
  ) {
    /** Returns the marker, or null after a weak marker is collected. */
    val marker: SnapshotRangeMarkerImpl?
      get() = handle.get()

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
   * The iterator orders entries by start offset and handle order.
   */
  fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<MarkerEntry>

  /**
   * Processes all valid markers by increasing actual start offset, then increasing handle order.
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
    /** Ignores invalidated marker handles. */
    @JvmField
    val EMPTY_HANDLE_CONSUMER: Consumer<SnapshotMarkerReference<*>> = Consumer { }

    /** Creates an empty marker root. */
    @JvmStatic
    fun empty(): PMarkerRoot = PMarkerRootImpl.empty()
  }
}
