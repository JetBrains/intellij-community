// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.util.Processor

/**
 * Mutable marker engine for immutable document snapshots.
 *
 * Each range marker storage owns marker roots for its [DocumentSnapshot] instances. Distinct snapshot instances remain
 * independent even when they have the same modification sequence.
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
   * @param retainStrong true when the marker root must retain the marker handle
   * @return stable marker handle
   */
  fun createRangeMarker(
    document: Document,
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    spec: MarkerSpec,
    retainStrong: Boolean = false,
  ): SnapshotMarker

  /**
   * Disposes [marker] and removes it from its current root.
   *
   * @return `true` if the marker was present and removed
   */
  fun removeRangeMarker(marker: SnapshotMarker): Boolean

  /**
   * Processes valid markers that non-strictly intersect the requested range.
   *
   * Each marker must contain every flavor bit in [tastePreference]. A zero value matches every marker.
   * Collected weak marker references are purged. Disposed markers are skipped.
   *
   * @param rootStore supplies the marker root for [snapshot]
   * @param snapshot selects the marker root
   * @param processor receives each matching marker
   * @return `false` when [processor] stops processing; otherwise, `true`
   */
  fun processRangeMarkersOverlappingWith(
    rootStore: SnapshotMarkerRootStore,
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in RangeMarkerEx>
  ): Boolean
}
