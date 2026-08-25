// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.DocumentSnapshotImpl
import com.intellij.openapi.editor.impl.StripedIDGenerator
import com.intellij.openapi.util.TextRange
import com.intellij.util.Processor
import com.intellij.util.containers.ReferenceQueueable
import org.jetbrains.annotations.TestOnly
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * Mutable snapshot-marker engine backed by immutable persistent [PMarkerRoot] values.
 *
 * The inherited, snapshot-less IntelliJ `RangeMarker` operations resolve against the current [DocumentSnapshot]
 * returned by the document captured by each marker handle. Snapshots must be backed by [DocumentSnapshotImpl].
 *
 * Marker creation and removal replace only the root currently associated with the selected snapshot. Existing
 * descendant snapshots are not changed.
 *
 * Creating a child snapshot derives its marker root from the parent's current root. Marker creation, marker removal,
 * and child creation from the same parent are linearized by atomic root operations.
 */
object SnapshotMarkerEngineImpl : SnapshotMarkerEngine, ReferenceQueueable {
  private val markerQueue = ReferenceQueue<SnapshotRangeMarkerImpl>()
  private val nextMarkerId: StripedIDGenerator = StripedIDGenerator().also { it.next() /* id must not be 0 */ }

  /**
   * Canonical weak handle reference retained by persistent marker states until the handle can be queue-purged.
   * Document and file-root ownership is weak so the cleanup metadata does not extend either lifetime.
   */
  private class MarkerReference(
    marker: SnapshotRangeMarkerImpl,
    document: DocumentImpl,
    queue: ReferenceQueue<SnapshotRangeMarkerImpl>,
  ) : WeakReference<SnapshotRangeMarkerImpl>(marker, queue) {
    val markerId: Long = marker.markerId
    val documentReference = WeakReference(document)
    val fileRootReference = marker.fileRoot?.let(::WeakReference)
  }

  /**
   * Derives and publishes the marker root for [afterSnapshot].
   *
   * The current root of [beforeSnapshot] is captured with one atomic read. Consequently:
   *
   * - a marker inserted before this operation is inherited by the child;
   * - a marker inserted after this operation is not inherited by the child;
   * - the root belonging to [beforeSnapshot] is not changed.
   *
   * [afterSnapshot] must not become visible before this method completes. Otherwise marker creation may race with
   * publishing the derived root.
   */
  override fun applyOp(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot, op: DocumentOp) {
    if (op is DocumentTextPatch) {
      validatePatch(beforeSnapshot, afterSnapshot, op)
    }
    else {
      require(beforeSnapshot.text() === afterSnapshot.text()) {
        "A non-text DocumentOp must preserve the text instance"
      }
    }
    publishRoot(beforeSnapshot, afterSnapshot) { it.applyOp(op) }
  }

  private inline fun publishRoot(
    beforeSnapshot: DocumentSnapshot,
    afterSnapshot: DocumentSnapshot,
    transform: (PMarkerRoot) -> PMarkerRoot,
  ) {
    processQueue()
    require(afterSnapshot !== beforeSnapshot) {
      "Before and after snapshots must be different instances"
    }
    val beforeRoot = markerRoot(beforeSnapshot).get()
    val afterRoot = transform(beforeRoot)
    val updated = markerRoot(afterSnapshot).compareAndSet(PMarkerRootImpl.empty(), afterRoot)
    require(updated) {
      "After snapshot marker root is already initialized"
    }
  }

  /**
   * Adds a marker only to [snapshot]'s current root.
   *
   * The original [spec] instance is preserved. This allows concrete [MarkerSpec] subtypes to identify different
   * marker kinds or storage-node algorithms.
   */
  override fun createRangeMarker(
    document: Document,
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    spec: MarkerSpec
  ): PMarker {
    processQueue()
    val rootReference = markerRoot(snapshot)
    require(startOffset >= 0) { "startOffset must be non-negative" }
    require(endOffset >= startOffset) { "endOffset must not precede startOffset" }
    require(endOffset <= snapshot.text().length()) { "Marker range exceeds snapshot length" }

    val documentImpl = document as DocumentImpl
    val fileRoot = FileMarkerRoot.getOrCreate(documentImpl)

    val markerId = nextMarkerId.next()
    val marker = SnapshotRangeMarkerImpl(document, fileRoot, markerId, spec, TextRange(startOffset, endOffset))
    val markerReference = MarkerReference(marker, documentImpl, markerQueue)

    while (true) {
      val oldRoot = rootReference.get()
      val newRoot = oldRoot.insert(markerId, startOffset, endOffset, spec, marker.flavorFlags, markerReference)
      if (rootReference.compareAndSet(oldRoot, newRoot)) {
        return marker
      }
    }
  }

  override fun processQueue(): Boolean {
    var ret = false
    while (true) {
      val reference = markerQueue.poll() as MarkerReference? ?: break
      val fileRoot = reference.fileRootReference?.get()
      val document = reference.documentReference.get()
      if (document != null) {
        ret = purgeRangeMarker(markerRoot(document.core.snapshot()), reference.markerId)
      }
      else if (fileRoot != null) {
        ret = purgeRangeMarker(fileRoot.rootReference(), reference.markerId)
      }
    }
    return ret
  }

  /**
   * Disposes [marker] and removes it from [snapshot]'s current root.
   *
   * Existing descendant roots remain unchanged, but resolution through the disposed handle is invalid. Future
   * children created from [snapshot] inherit the root without the marker.
   */
  override fun removeRangeMarker(snapshot: DocumentSnapshot, marker: PMarker): Boolean =
    removeRangeMarker(marker, snapshot)

  fun removeRangeMarker(marker: PMarker, snapshot: DocumentSnapshot? = null): Boolean {
    processQueue()
    val storedMarker = marker as SnapshotRangeMarkerImpl
    val markerId = storedMarker.markerId
    storedMarker.markDisposed()
    val rootReference = snapshot?.let(::markerRoot) ?: storedMarker.currentRootReference()
    while (true) {
      val oldRoot = rootReference.get()
      val markerReference = oldRoot.markerReference(markerId)
      val newRoot = oldRoot.remove(markerId)
      if (rootReference.compareAndSet(oldRoot, newRoot)) {
        markerReference?.clear()
        return oldRoot !== newRoot
      }
    }
  }

  private fun purgeRangeMarker(
    rootReference: AtomicReference<PMarkerRoot>,
    markerId: Long,
  ): Boolean {
    while (true) {
      val oldRoot = rootReference.get()
      val newRoot = oldRoot.purge(markerId)
      if (rootReference.compareAndSet(oldRoot, newRoot)) {
        return oldRoot !== newRoot
      }
    }
  }

  override fun processRangeMarkersOverlappingWith(
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in RangeMarkerEx>,
  ): Boolean {
    processQueue()
    return markerRoot(snapshot).get().processRangeMarkersOverlappingWith(startOffset, endOffset, tastePreference) { entry ->
      val marker = entry.markerReference?.get()
      if (marker == null) {
        purgeRangeMarker(markerRoot(snapshot), entry.markerId)
        true
      }
      else {
        marker.disposed || processor.process(marker)
      }
    }
  }

  /**
   * Resolves [marker] against [snapshot]'s current marker root.
   *
   * The root reference is obtained from the snapshot's atomic holder. Since the selected [PMarkerRoot] is immutable,
   * resolution needs only one atomic read.
   */
  override fun resolveRangeMarker(marker: PMarker, snapshot: DocumentSnapshot): PMarkerResolution {
    val storedMarker = marker as SnapshotRangeMarkerImpl
    return resolveRangeMarker(storedMarker, markerRoot(snapshot).get())
  }

  internal fun resolveRangeMarker(marker: SnapshotRangeMarkerImpl, root: PMarkerRoot): PMarkerResolution {
    val resolution = root.resolve(marker.markerId, marker.initialRange)
    return if (marker.disposed) {
      PMarkerResolution.Invalid(DISPOSED_REASON, resolution.startOffset, resolution.endOffset)
    }
    else {
      resolution
    }
  }

  /**
   * Returns the marker-root reference owned by [snapshot].
   */
  private fun markerRoot(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> =
    (snapshot as DocumentSnapshotImpl).markerRoot

  @TestOnly
  fun containsMarkerId(snapshot: DocumentSnapshot, markerId: Long): Boolean =
    (markerRoot(snapshot).get() as PMarkerRootImpl).containsMarkerId(markerId)

  private fun validatePatch(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot, patch: DocumentTextPatch) {
    val beforeLength = beforeSnapshot.text().length()
    val afterLength = afterSnapshot.text().length()
    val startOffset = patch.startOffset()
    val endOffset = patch.endOffset()
    require(startOffset >= 0) { "DocumentTextPatch startOffset must be non-negative" }
    require(endOffset >= startOffset) { "DocumentTextPatch endOffset must not precede startOffset" }
    require(endOffset <= beforeLength) { "DocumentTextPatch range exceeds before snapshot length" }
    val expectedLength = beforeLength.toLong() - (endOffset - startOffset) + patch.newFragment().length
    require(expectedLength == afterLength.toLong()) {
      "After snapshot length is inconsistent with DocumentTextPatch"
    }
  }

  private const val DISPOSED_REASON = "Marker is disposed"
}
