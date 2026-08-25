// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.util.Processor

/**
 * Mutable marker engine for immutable document snapshots.
 *
 * Each supported [DocumentSnapshot] instance owns a reference to one [PMarkerRoot].
 * Distinct snapshot instances remain independent even when they have the same
 * modification sequence.
 *
 * [SnapshotMarkerEngine] is mutable, while every [PMarkerRoot] is an immutable,
 * persistent value.
 *
 * Marker insertion or removal changes only the root currently associated with
 * the selected snapshot. Existing descendant snapshots are not updated.
 *
 * A future child snapshot derives its marker root from the current marker root
 * of its parent.
 */
interface SnapshotMarkerEngine {
  /**
   * Derives and stores the marker root for [afterSnapshot].
   *
   * The implementation obtains the current root associated with
   * [beforeSnapshot], calls [PMarkerRoot.applyPatch], and stores the returned
   * root in [afterSnapshot].
   *
   * The root associated with [beforeSnapshot] is not changed.
   *
   * [patch] must describe exactly the transformation from [beforeSnapshot]
   * to [afterSnapshot]. Its offsets use coordinates from [beforeSnapshot].
   *
   * Marker creation, marker removal, and child-snapshot creation from the
   * same snapshot must be linearized.
   *
   * @param beforeSnapshot parent text snapshot
   * @param afterSnapshot child text snapshot created by the edit
   * @param patch text patch describing the change
   */
  fun applyPatch(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot, patch: DocumentTextPatch)

  /**
   * Stores an independent marker root for [afterSnapshot] by capturing the current root of [beforeSnapshot].
   * Both snapshots must share the same text instance.
   */
  fun inherit(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot)

  /**
   * Creates an engine-global marker ID and inserts the marker into the
   * current root associated with [snapshot].
   *
   * Existing descendants of [snapshot] are not modified. Future children
   * created from [snapshot] inherit the marker.
   *
   * The following range precondition must hold:
   *
   *     0 <= startOffset <= endOffset <= snapshot.textLength
   *
   * @param document document exposed by the returned marker handle
   * @param snapshot snapshot in whose current root the marker is inserted
   * @param startOffset inclusive range start
   * @param endOffset exclusive range end
   * @param spec immutable marker configuration
   * @return stable marker handle
   */
  fun createRangeMarker(
    document: Document,
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    spec: MarkerSpec,
  ): PMarker

  /**
   * Disposes [marker] and removes it from the current root associated with [snapshot].
   *
   * Existing descendant roots remain unchanged, but resolution through the disposed handle is invalid. Future
   * children created from [snapshot] inherit the root without the marker.
   *
   * @return `true` if the marker was present and removed, or `false` if it
   * was already absent or disposed
   */
  fun removeRangeMarker(snapshot: DocumentSnapshot, marker: PMarker): Boolean

  /**
   * Resolves [marker] using the current root associated with [snapshot].
   */
  fun resolveRangeMarker(marker: PMarker, snapshot: DocumentSnapshot): PMarkerResolution

  fun processRangeMarkersOverlappingWith(
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in RangeMarkerEx>
  ): Boolean
}
