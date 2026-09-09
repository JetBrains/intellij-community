// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.StripedIDGenerator
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * Mutable snapshot-marker engine backed by immutable persistent [PMarkerRoot] values in external marker stores.
 *
 * The inherited, snapshot-less IntelliJ `RangeMarker` operations resolve against the current [DocumentSnapshot]
 * returned by the document captured by each marker handle.
 *
 * Marker creation and removal replace only the root associated with the selected snapshot in its document's store.
 * Existing descendant snapshots are not changed.
 *
 * Creating a child snapshot derives its marker root from the parent's current root. Marker creation, marker removal,
 * and child creation from the same parent are linearized by atomic root operations.
 */
object SnapshotMarkerEngineImpl : SnapshotMarkerEngine {
  private val markerQueue: ReferenceQueue<SnapshotRangeMarkerImpl> = ReferenceQueue()
  private val nextMarkerId: StripedIDGenerator = StripedIDGenerator().also { it.next() /* id must not be 0 */ }

  @ApiStatus.Internal
  fun nextMarkerId(): Long = nextMarkerId.next()

  /**
   * Canonical weak handle reference retained by persistent marker states until the handle can be queue-purged.
   * Document and file-root ownership is weak so the cleanup metadata does not extend either lifetime.
   */
  private class QueuedMarkerReference(
    marker: SnapshotRangeMarkerImpl,
    document: DocumentImpl?,
    queue: ReferenceQueue<SnapshotRangeMarkerImpl>,
  ) : WeakSnapshotMarkerReference(marker, queue) {
    val markerId: Long = marker.markerId
    val documentReference: WeakReference<DocumentImpl>? = document?.let(::WeakReference)
    val fileRootReference: WeakReference<FileMarkerRoot>? = marker.fileRoot?.let(::WeakReference)
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
    spec: MarkerSpec,
    retainStrong: Boolean,
  ): PMarker {
    return createRangeMarker(document, snapshot, startOffset, endOffset, spec, retainStrong) { fileRoot, markerId ->
      SnapshotRangeMarkerImpl(document as DocumentImpl, fileRoot, markerId, spec, TextRange(startOffset, endOffset))
    }
  }

  internal fun <T : SnapshotRangeMarkerImpl> createRangeMarker(
    document: Document,
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    spec: MarkerSpec,
    retainStrong: Boolean = false,
    markerFactory: (FileMarkerRoot?, Long) -> T,
  ): T {
    processQueue()
    val documentImpl = document as DocumentImpl
    val rootReference = documentImpl.rangeMarkers.rootStore().rootReference(snapshot)
    require(startOffset >= 0) { "startOffset must be non-negative" }
    require(endOffset >= startOffset) { "endOffset must not precede startOffset" }
    require(endOffset <= snapshot.text().length()) { "Marker range exceeds snapshot length" }

    val fileRoot = FileMarkerRoot.getOrCreate(documentImpl)

    val markerId = nextMarkerId()
    val marker = markerFactory(fileRoot, markerId)
    val markerReference = if (retainStrong) {
      StrongSnapshotMarkerReference(marker)
    }
    else {
      QueuedMarkerReference(marker, documentImpl, markerQueue)
    }

    while (true) {
      val oldRoot = rootReference.get()
      val newRoot = oldRoot.insert(markerId, startOffset, endOffset, spec, marker.flavorFlags, markerReference)
      if (rootReference.compareAndSet(oldRoot, newRoot)) {
        return marker
      }
    }
  }

  @ApiStatus.Internal
  fun createRangeMarkerForVirtualFile(
    file: VirtualFile,
    startOffset: Int,
    startLine: Int,
    startColumn: Int,
    endLine: Int,
    endColumn: Int,
    persistent: Boolean,
  ): RangeMarkerEx {
    processQueue()
    require(startOffset >= 0) { "startOffset must be non-negative" }

    val cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file) as? DocumentImpl
    val fileRoot = FileMarkerRoot.getOrCreate(file)
    val markerId = nextMarkerId()
    val spec = MarkerSpec(
      isGreedyToLeft = false,
      isGreedyToRight = false,
      policy = if (persistent) PersistentMarkerPolicy else DefaultMarkerPolicy,
    )
    val initialLineColumns = if (persistent && cachedDocument == null &&
                                 startLine >= 0 && startColumn >= 0 && endLine >= 0 && endColumn >= 0) {
      SnapshotLazyRangeMarker.LineColumns(startLine, startColumn, endLine, endColumn)
    }
    else {
      null
    }
    val marker = SnapshotLazyRangeMarker(fileRoot, markerId, spec, TextRange(startOffset, startOffset), initialLineColumns)
    val markerReference = QueuedMarkerReference(marker, cachedDocument, markerQueue)
    val rootReference = fileRoot.rootReference()
    while (true) {
      val oldRoot = rootReference.get()
      val newRoot = oldRoot.insert(markerId, startOffset, startOffset, spec, marker.flavorFlags, markerReference)
      if (rootReference.compareAndSet(oldRoot, newRoot)) return marker
    }
  }

  fun processQueue(): Boolean {
    var ret = false
    while (true) {
      val reference = markerQueue.poll() as QueuedMarkerReference? ?: break
      val fileRoot = reference.fileRootReference?.get()
      val document = reference.documentReference?.get()
      if (document != null) {
        ret = purgeRangeMarker(document.rangeMarkers.rootStore().rootReference(document.core.snapshot()), reference.markerId)
      }
      else if (fileRoot != null) {
        ret = purgeRangeMarker(fileRoot.rootReference(), reference.markerId)
      }
    }
    return ret
  }

  /** Creates a marker reference with the requested ownership for a root store. */
  @ApiStatus.Internal
  fun createMarkerReference(marker: SnapshotRangeMarkerImpl, retainStrong: Boolean): SnapshotMarkerReference {
    return if (retainStrong) StrongSnapshotMarkerReference(marker) else WeakSnapshotMarkerReference(marker)
  }

  override fun removeRangeMarker(marker: PMarker): Boolean {
    processQueue()
    val storedMarker = marker as SnapshotRangeMarkerImpl
    val markerId = storedMarker.markerId
    storedMarker.markDisposed()
    val rootReference = storedMarker.currentRootReference()
    while (true) {
      val oldRoot = rootReference.get()
      val newRoot = oldRoot.remove(markerId)
      if (rootReference.compareAndSet(oldRoot, newRoot)) {
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
    rootStore: SnapshotMarkerRootStore,
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in RangeMarkerEx>,
  ): Boolean {
    processQueue()
    val root = rootStore.root(snapshot) ?: return true
    return root.processRangeMarkersOverlappingWith(startOffset, endOffset, tastePreference) { entry ->
      val marker = entry.markerReference?.get()
      if (marker == null) {
        rootStore.purge(snapshot, entry.markerId)
        true
      }
      else {
        marker.disposed || processor.process(marker)
      }
    }
  }

  @ApiStatus.Internal
  fun resolveRangeMarker(marker: SnapshotRangeMarkerImpl, root: PMarkerRoot): PMarkerResolution {
    val resolution = root.resolve(marker.markerId, marker.initialRange)
    return if (marker.disposed) {
      PMarkerResolution.Invalid(DISPOSED_REASON, resolution.startOffset, resolution.endOffset)
    }
    else {
      resolution
    }
  }

  private const val DISPOSED_REASON: String = "Marker is disposed"
}
