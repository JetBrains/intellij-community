// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.ReferenceQueue

/**
 * Mutable snapshot-marker engine backed by immutable persistent [PMarkerRoot] values in external marker stores.
 *
 * The inherited, snapshot-less IntelliJ `RangeMarker` operations resolve against the current [DocumentSnapshot]
 * returned by the document captured by each marker handle.
 *
 * Marker creation and removal update the root for the document's current snapshot.
 * Existing descendant snapshots are not changed.
 *
 * Creating a child snapshot derives its marker root from the parent's current root. Marker creation, marker removal,
 * and child creation from the same parent are linearized by atomic root operations.
 */
object SnapshotMarkerEngineImpl : SnapshotMarkerEngine {
  internal val markerQueue: ReferenceQueue<SnapshotRangeMarkerImpl> = ReferenceQueue()

  /** Creates a marker in the current snapshot and retries if a text change publishes another snapshot. */
    override fun createRangeMarker(
    document: Document,
    startOffset: Int,
    endOffset: Int,
    spec: Byte,
    retainStrong: Boolean,
    policy: MarkerPolicy,
  ): SnapshotMarker {
    return createRangeMarker(document, startOffset, endOffset, spec, policy) { fileRoot ->
      val initialRange = TextRangeScalarUtil.toScalarRange(startOffset, endOffset)
      SnapshotRangeMarkerImpl(document as DocumentImpl, fileRoot, spec, initialRange, retainStrong)
    }
  }

  /**
   * Creates a marker in the current snapshot and retries when a text change publishes another snapshot.
   *
   * @param markerFactory receives the file root.
   * The file root is `null` when the document has no associated file or [FileDocumentManager] is unavailable.
   */
  internal fun <T : SnapshotRangeMarkerImpl> createRangeMarker(
    document: Document,
    startOffset: Int,
    endOffset: Int,
    spec: Byte,
    policy: MarkerPolicy,
    markerFactory: (VirtualFileMarkerRoot?) -> T,
  ): T {
    processQueue()
    val documentImpl = document as DocumentImpl
    val rootStore = documentImpl.rangeMarkers.rootStore()
    require(startOffset >= 0) { "startOffset must be non-negative" }
    require(endOffset >= startOffset) { "endOffset must not precede startOffset" }

    val fileRoot = VirtualFileMarkerRoot.getOrCreate(documentImpl)

    val marker = markerFactory(fileRoot)
    val insertedRoot = checkNotNull(rootStore.updateCurrentRootAtomically { currentSnapshot, root ->
      require(endOffset <= currentSnapshot.text().length()) { "Marker range exceeds snapshot length" }
      root.insert(marker.markerHandle, startOffset, endOffset, spec, marker.flavorFlags, policy = policy)
    })
    marker.cacheResolution(insertedRoot, PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(startOffset, endOffset)))
    return marker
  }

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
    val marker = SnapshotLazyRangeMarker(fileRoot, spec, initialRange, initialLineColumns)
    val insertedRoot = checkNotNull(fileRoot.updateRootAtomically(null) { it: PMarkerRoot ->
      it.insert(marker.markerHandle, startOffset, startOffset, spec, marker.flavorFlags, policy = policy)
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
    var reference = markerQueue.poll() as? WeakSnapshotMarkerReference<*> ?: return false
    var batchStorage: MarkerRootUpdater? = null
    val handles = ArrayList<SnapshotMarkerReference<*>>()
    var purgedAny = false
    while (true) {
      val rootStorage = reference.rootStorageReference.get()
      if (rootStorage != null) {
        if (rootStorage !== batchStorage) {
          if (batchStorage != null && batchStorage.purgeCollected(handles)) purgedAny = true
          handles.clear()
          batchStorage = rootStorage
        }
        handles.add(reference)
      }
      reference = markerQueue.poll() as? WeakSnapshotMarkerReference<*> ?: break
    }
    if (batchStorage != null && batchStorage.purgeCollected(handles)) purgedAny = true
    return purgedAny
  }

  override fun removeRangeMarker(marker: SnapshotMarker): Boolean {
    processQueue()
    val storedMarker = marker as SnapshotRangeMarkerImpl
    val handle = storedMarker.markerHandle
    storedMarker.markDisposed()
    return storedMarker.updateCurrentRoot { it.remove(handle) }
  }

  override fun processAllRangeMarkers(
    rootStore: SnapshotMarkerRootStore<*>,
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
    rootStore: SnapshotMarkerRootStore<*>,
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
    private var collectedHandles: ArrayList<SnapshotMarkerReference<*>>? = null

    override fun process(entry: PMarkerRoot.MarkerEntry): Boolean {
      val marker = entry.marker
      if (marker == null) {
        val handles = collectedHandles ?: ArrayList<SnapshotMarkerReference<*>>().also { collectedHandles = it }
        handles.add(entry.handle)
        return true
      }
      return marker.isDisposed() || processor.process(marker)
    }

    override fun process(entry: PMarkerRoot.MarkerEntry, ancestorDelta: Int): Boolean = process(entry)

    fun purgeCollected(rootStore: SnapshotMarkerRootStore<*>, snapshot: DocumentSnapshot) {
      val handles = collectedHandles ?: return
      rootStore.purgeMany(snapshot, handles)
    }
  }

  /**
   * Returns the marker state in [root], or [PMarkerResolution.ABSENT] when the marker is absent.
   * Marks a retained state as invalid when the marker is disposed.
   */
    fun resolveRangeMarker(marker: SnapshotRangeMarkerImpl, root: PMarkerRoot): Long {
    val resolution = root.resolve(marker.markerHandle)
    return if (marker.isDisposed()) {
      PMarkerResolution.invalid(resolution)
    }
    else {
      resolution
    }
  }

  /** Returns the disposal or invalidation reason, or `null` when the marker is valid or absent. */
    fun invalidationReason(marker: SnapshotRangeMarkerImpl, root: PMarkerRoot): String? {
    return if (marker.isDisposed()) DISPOSED_REASON else root.invalidationReason(marker.markerHandle)
  }

  private const val DISPOSED_REASON: String = "Marker is disposed"
}
