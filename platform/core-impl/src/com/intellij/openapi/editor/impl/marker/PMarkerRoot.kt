// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.util.TextRange
import com.intellij.util.Processor
import java.lang.ref.WeakReference
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
   * Derives the marker root for the document state after [op]. Non-text operations return this root unchanged.
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
   */
  fun applyOp(op: DocumentOp): PMarkerRoot

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
   * [markerReference] is the weak handle reference owned by the snapshot marker engine; standalone roots may omit it.
   */
  fun insert(
    markerId: Long,
    startOffset: Int,
    endOffset: Int,
    spec: MarkerSpec,
    flavorFlags: Byte,
    markerReference: WeakReference<SnapshotRangeMarkerImpl>? = null,
  ): PMarkerRoot

  fun insert(markerId: Long, startOffset: Int, endOffset: Int, spec: MarkerSpec): PMarkerRoot =
    insert(markerId, startOffset, endOffset, spec, flavorFlags = 0, markerReference = null)

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
   * Returns the weak handle reference retained by any state of [markerId].
   *
   * Standalone roots and roots that have never observed [markerId] return `null`.
   */
  fun markerReference(markerId: Long): WeakReference<SnapshotRangeMarkerImpl>?

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

  data class MarkerEntry(
    val markerId: Long,
    val startOffset: Int,
    val endOffset: Int,
    val spec: MarkerSpec,
    val flavorFlags: Byte,
    val markerReference: WeakReference<SnapshotRangeMarkerImpl>? = null,
  )

  /**
   * Processes valid markers intersecting the requested range and containing every bit in [tastePreference].
   * A zero preference matches every marker.
   */
  fun processRangeMarkersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in MarkerEntry>,
  ): Boolean

  fun processRangeMarkersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    processor: Processor<in MarkerEntry>,
  ): Boolean = processRangeMarkersOverlappingWith(startOffset, endOffset, tastePreference = 0, processor = processor)
}
