// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.util.TextRange
import com.intellij.util.Processor
import java.util.function.LongConsumer

/**
 * Immutable persistent marker-state root.
 *
 * A root represents the complete marker state associated with one immutable document snapshot instance. Each
 * snapshot stores an atomic reference to its current root.
 *
 * None of the operations in this interface mutate the receiver. Operations
 * that change marker state return a new root that may structurally share data
 * with the receiver.
 */
interface PMarkerRoot {
  /**
   * Resolves [markerId] in this root.
   *
   * @return [PMarkerResolution.Valid], [PMarkerResolution.Invalid], or
   * [PMarkerResolution.Absent]
   * @param absentRange range to return when this root has never observed [markerId]; removed markers use their stored
   * last range instead
   */
  fun resolve(markerId: Long, absentRange: TextRange): PMarkerResolution

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
   */
  fun applyPatch(
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    invalidatedMarkerConsumer: LongConsumer = EMPTY_LONG_CONSUMER,
  ): PMarkerRoot

  /**
   * Inserts a marker whose ID is absent from this root.
   *
   * The following range precondition must hold:
   *
   *     0 <= startOffset <= endOffset <= snapshot text length
   *
   * A duplicate [markerId] should cause [IllegalArgumentException] or a more
   * specific implementation-defined exception.
   *
   * [flavorFlags] is the value obtained from the marker's `RangeMarkerEx.getFlavorFlags()` method at insertion time.
   * [markerReference] provides the marker handle with either weak or strong ownership. Standalone roots may omit it.
   * [measure] contributes to prefix aggregate queries.
   */
  fun insert(
    markerId: Long,
    startOffset: Int,
    endOffset: Int,
    spec: MarkerSpec,
    flavorFlags: Byte,
    markerReference: SnapshotMarkerReference? = null,
    measure: Int = 0,
  ): PMarkerRoot

  /**
   * Replaces the flavor flags of a valid marker, preserving its range and specification.
   *
   * If [markerId] is absent or invalid, this method returns the receiver unchanged.
   */
  fun updateFlavor(markerId: Long, flavorFlags: Byte): PMarkerRoot

  /**
   * Replaces the specification of a valid marker, preserving its range, flavor flags, and handle reference.
   *
   * If [markerId] is absent or invalid, this method returns the receiver unchanged.
   */
  fun updateSpec(markerId: Long, spec: MarkerSpec): PMarkerRoot

  /**
   * Replaces the measure of a valid marker, preserving its range and other properties.
   *
   * If [markerId] is absent or invalid, this method returns the receiver unchanged.
   */
  fun updateMeasure(markerId: Long, measure: Int): PMarkerRoot

  /**
   * Removes the marker's endpoint anchors and secondary-index entries, retaining an absent tombstone with its last
   * range.
   *
   * If [markerId] is absent, this method should return the receiver
   * unchanged.
   *
   * This is branch-local removal, not global marker disposal.
   */
  fun remove(markerId: Long): PMarkerRoot

  /**
   * Removes every trace of [markerId], including its stored resolution.
   *
   * This operation is intended for garbage-collected marker handles. Unlike [remove], it does not retain an absent
   * tombstone because no handle remains that could resolve against it.
   */
  fun purge(markerId: Long): PMarkerRoot

  /**
   * Keeps this root's marker states and adds valid markers that exist only in [other].
   */
  fun mergeValidMarkersFrom(other: PMarkerRoot): PMarkerRoot

  /**
   * Contains the immutable state of one valid marker.
   *
   * Root operations expose the offsets in the coordinate space of the root's document snapshot.
   */
  data class MarkerEntry(
    /** Identifies the marker across roots and snapshots. */
    val markerId: Long,

    /** Gives the inclusive start offset. */
    val startOffset: Int,

    /** Gives the exclusive end offset. */
    val endOffset: Int,

    /** Defines how document edits transform the marker. */
    val spec: MarkerSpec,

    /** Selects query categories that the root uses for subtree pruning. */
    val flavorFlags: Byte,

    /** Holds the optional marker handle and its ownership mode. */
    val markerReference: SnapshotMarkerReference? = null,

    /** Contributes this integer to prefix aggregates. Non-empty markers must use the identity measure, zero. */
    val measure: Int = 0,
  ) {
    init {
      require(measure == 0 || startOffset == endOffset) {
        "Only zero-length markers can have a non-zero measure"
      }
    }
  }

  /** Returns the prefix aggregate for valid markers whose start offset is not greater than [offset]. */
  fun getPrefixAggregate(offset: Int): Int

  /**
   * Processes valid markers that non-strictly intersect the requested range and contain every bit in [tastePreference].
   * A zero preference matches every marker.
   */
  fun processRangeMarkersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in MarkerEntry>,
  ): Boolean

  /**
   * Returns a lazy iterator over valid markers that intersect the half-open range `[startOffset, endOffset)` and match [tastePreference].
   * The iterator orders entries by start offset and marker ID.
   */
  fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<MarkerEntry>

  companion object {
    /** Ignores invalidated marker IDs. */
    @JvmField
    val EMPTY_LONG_CONSUMER: LongConsumer = LongConsumer { }
  }
}
