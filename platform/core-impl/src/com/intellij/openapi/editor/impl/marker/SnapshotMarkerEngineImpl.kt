// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.StripedIDGenerator
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import it.unimi.dsi.fastutil.longs.LongArrayList
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.IdentityHashMap

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
    val fileRootReference: WeakReference<VirtualFileMarkerRoot>? = marker.fileRoot?.let(::WeakReference)
  }

  /**
   * Adds a marker only to [snapshot]'s current root.
   */
  override fun createRangeMarker(
    document: Document,
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    spec: Byte,
    retainStrong: Boolean,
    policy: MarkerPolicy,
  ): SnapshotMarker {
    return createRangeMarker(document, snapshot, startOffset, endOffset, spec, retainStrong) { fileRoot, markerId ->
      val initialRange = TextRangeScalarUtil.toScalarRange(startOffset, endOffset)
      SnapshotRangeMarkerImpl(document as DocumentImpl, fileRoot, markerId, spec, initialRange, policy)
    }
  }

  internal fun <T : SnapshotRangeMarkerImpl> createRangeMarker(
    document: Document,
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    spec: Byte,
    retainStrong: Boolean = false,
    markerFactory: (VirtualFileMarkerRoot?, Long) -> T,
  ): T {
    processQueue()
    val documentImpl = document as DocumentImpl
    val rootReference = documentImpl.rangeMarkers.rootStore().rootReference(snapshot)
    require(startOffset >= 0) { "startOffset must be non-negative" }
    require(endOffset >= startOffset) { "endOffset must not precede startOffset" }
    require(endOffset <= snapshot.text().length()) { "Marker range exceeds snapshot length" }

    val fileRoot = VirtualFileMarkerRoot.getOrCreate(documentImpl)

    val markerId = nextMarkerId()
    val marker = markerFactory(fileRoot, markerId)
    val markerReference = if (retainStrong) {
      StrongSnapshotMarkerReference(marker)
    }
    else {
      QueuedMarkerReference(marker, documentImpl, markerQueue)
    }

    val update: (PMarkerRoot) -> PMarkerRoot = {
      it.insert(markerId, startOffset, endOffset, spec, marker.flavorFlags, markerReference, policy = marker.policy)
    }
    val rootStorage = fileRoot ?: documentImpl.rangeMarkers.rootStore()
    val insertedRoot = checkNotNull(rootStorage.updateRootAtomically(rootReference, update))
    marker.cacheResolution(insertedRoot, PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(startOffset, endOffset)))
    return marker
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
    val fileRoot = VirtualFileMarkerRoot.getOrCreate(file)
    val markerId = nextMarkerId()
    val spec = MarkerSpec.create(
      isGreedyToLeft = false,
      isGreedyToRight = false,
    )
    val policy = if (persistent) PersistentMarkerPolicy else DefaultMarkerPolicy
    val initialLineColumns = if (persistent && cachedDocument == null &&
                                 startLine >= 0 && startColumn >= 0 && endLine >= 0 && endColumn >= 0) {
      SnapshotLazyRangeMarker.LineColumns(startLine, startColumn, endLine, endColumn)
    }
    else {
      null
    }
    val initialRange = TextRangeScalarUtil.toScalarRange(startOffset, startOffset)
    val marker = SnapshotLazyRangeMarker(fileRoot, markerId, spec, initialRange, initialLineColumns, policy)
    val markerReference = QueuedMarkerReference(marker, cachedDocument, markerQueue)
    val insertedRoot = checkNotNull(fileRoot.updateRootAtomically(null) { it: PMarkerRoot ->
      it.insert(markerId, startOffset, startOffset, spec, marker.flavorFlags, markerReference, policy = marker.policy)
    })
    marker.cacheResolution(insertedRoot, PMarkerResolution.valid(initialRange))
    return marker
  }

  /**
   * Purges collected weak marker handles from their current roots.
   *
   * @return `true` when at least one root changed
   */
  fun processQueue(): Boolean {
    var reference = markerQueue.poll() as? QueuedMarkerReference ?: return false
    var firstRootStorage: MarkerRootUpdater? = null
    val firstMarkerIds = LongArrayList()
    var markerIdsByRoot: IdentityHashMap<MarkerRootUpdater, LongArrayList>? = null
    while (true) {
      val rootStorage = reference.fileRootReference?.get()
                        ?: reference.documentReference?.get()?.rangeMarkers?.rootStore()
      if (rootStorage != null) {
        if (firstRootStorage == null) firstRootStorage = rootStorage
        if (rootStorage === firstRootStorage) {
          firstMarkerIds.add(reference.markerId)
        }
        else {
          // allocate map only when there are at least two distinct storages found
          val batches = markerIdsByRoot ?: IdentityHashMap<MarkerRootUpdater, LongArrayList>().also { markerIdsByRoot = it }
          batches.getOrPut(rootStorage) { LongArrayList() }.add(reference.markerId)
        }
      }
      reference = markerQueue.poll() as? QueuedMarkerReference ?: break
    }
    var purgedAny = firstRootStorage?.updateCurrentRoot { it.purgeMany(firstMarkerIds) } ?: false
    for ((rootStorage, markerIds) in markerIdsByRoot ?: return purgedAny) {
      if (rootStorage.updateCurrentRoot { it.purgeMany(markerIds) }) purgedAny = true
    }
    return purgedAny
  }

  /** Creates a marker reference with the requested ownership for a root store. */
  @ApiStatus.Internal
  fun createMarkerReference(marker: SnapshotRangeMarkerImpl, retainStrong: Boolean): SnapshotMarkerReference {
    return if (retainStrong) StrongSnapshotMarkerReference(marker) else WeakSnapshotMarkerReference(marker)
  }

  override fun removeRangeMarker(marker: SnapshotMarker): Boolean {
    processQueue()
    val storedMarker = marker as SnapshotRangeMarkerImpl
    val markerId = storedMarker.markerId
    storedMarker.markDisposed()
    return storedMarker.updateCurrentRoot { it.remove(markerId) }
  }

  override fun processAllRangeMarkers(
    rootStore: SnapshotMarkerRootStore,
    snapshot: DocumentSnapshot,
    processor: Processor<in RangeMarkerEx>,
  ): Boolean {
    processQueue()
    val root = rootStore.root(snapshot) ?: return true
    val markerProcessor = MarkerProcessor(processor)
    try {
      return root.processAllRangeMarkers(markerProcessor)
    }
    finally {
      markerProcessor.purgeCollected(rootStore, snapshot)
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
    val markerProcessor = MarkerProcessor(processor)
    try {
      return root.processRangeMarkersOverlappingWith(startOffset, endOffset, tastePreference, markerProcessor)
    }
    finally {
      markerProcessor.purgeCollected(rootStore, snapshot)
    }
  }

  private class MarkerProcessor(
    private val processor: Processor<in RangeMarkerEx>,
  ) : Processor<PMarkerRoot.MarkerEntry>, PMarkerRoot.MarkerDeltaProcessor {
    private var collectedMarkerIds: LongArrayList? = null

    override fun process(entry: PMarkerRoot.MarkerEntry): Boolean {
      val marker = entry.markerReference?.get()
      if (marker == null) {
        val markerIds = collectedMarkerIds ?: LongArrayList().also { collectedMarkerIds = it }
        markerIds.add(entry.markerId)
        return true
      }
      return marker.isDisposed() || processor.process(marker)
    }

    override fun process(entry: PMarkerRoot.MarkerEntry, ancestorDelta: Int): Boolean = process(entry)

    fun purgeCollected(rootStore: SnapshotMarkerRootStore, snapshot: DocumentSnapshot) {
      val markerIds = collectedMarkerIds ?: return
      rootStore.purgeMany(snapshot, markerIds)
    }
  }

  @ApiStatus.Internal
  fun resolveRangeMarker(marker: SnapshotRangeMarkerImpl, root: PMarkerRoot): Long {
    val resolution = root.resolve(marker.markerId)
    return if (marker.isDisposed()) {
      val range = if (PMarkerResolution.isAbsent(resolution)) marker.initialRange else resolution
      PMarkerResolution.invalid(range)
    }
    else {
      resolution
    }
  }

  /** Returns the disposal or invalidation reason, or `null` when the marker is valid or absent. */
  @ApiStatus.Internal
  fun invalidationReason(marker: SnapshotRangeMarkerImpl, root: PMarkerRoot): String? {
    return if (marker.isDisposed()) DISPOSED_REASON else root.invalidationReason(marker.markerId)
  }

  private const val DISPOSED_REASON: String = "Marker is disposed"
}
