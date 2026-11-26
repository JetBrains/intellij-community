// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ObjectUtilsRt
import java.lang.ref.Reference
import java.lang.ref.WeakReference

/**
 * Virtual file-backed marker state shared by marker handles and a live document without retaining either the document or its
 * text. A weak reference stored in the [VirtualFile] makes the state discoverable when a document is recreated.
 */
class VirtualFileMarkerRoot private constructor(
  internal val file: VirtualFile,
  initialRootReference: PMarkerRootReference,
) : MarkerRootUpdater(), DocumentListener {
  @Volatile
  private var rootReference: PMarkerRootReference = initialRootReference

  @Volatile
  private var documentReference: WeakReference<DocumentImpl>? = null

  override fun createHandle(marker: SnapshotRangeMarkerImpl, retainStrong: Boolean): SnapshotMarkerReference<*> =
    SnapshotMarkerReference.create(marker, retainStrong)

  /** Returns the live document's root reference when available. Otherwise returns the last reference retained for the file. */
  override fun currentRootReference(): PMarkerRootReference {
    val observedDocumentReference = documentReference ?: return rootReference
    val document = observedDocumentReference.get() ?: return rootReference
    val currentRootReference = markerRoot(document)
    if (documentReference === observedDocumentReference) {
      rootReference = currentRootReference
    }
    return currentRootReference
  }

  /**
   * Updates the file's marker root under the same monitor as [attach] and [restoreAndAttach].
   * The live document's store publishes the update while the document is attached.
   */
  @Synchronized
  override fun updateCurrentRoot(update: (PMarkerRoot) -> PMarkerRoot): PMarkerRoot? {
    val document = documentReference?.get() ?: return updateRootAtomically(rootReference, update)
    try {
      return document.rangeMarkers.rootStore().updateCurrentRoot(update)
    }
    finally {
      this.rootReference = markerRoot(document)
      ObjectUtilsRt.reachabilityFence(document)
    }
  }

  @Synchronized
  private fun attach(document: DocumentImpl) {
    rootReference = markerRoot(document)
    if (documentReference?.get() !== document) {
      document.addDocumentListener(this)
      documentReference = WeakReference(document)
    }
  }

  /**
   * Converts pending line and column positions to document offsets.
   * Installs the resulting root in the document before attaching it.
   */
  @Synchronized
  private fun restoreAndAttach(document: DocumentImpl, tabSize: Int) {
    val sourceRoot = rootReference.get()
    var restoredEntries: ArrayList<PMarkerRoot.MarkerEntry>? = null
    val resolvedMarkers = ArrayList<SnapshotLazyRangeMarker>()
    sourceRoot.processAllRangeMarkers { entry, ancestorDelta ->
      val marker = entry.marker as? SnapshotLazyRangeMarker ?: return@processAllRangeMarkers true
      val range = marker.initialRange(document, tabSize)
      if (range == PMarkerResolution.UNRESOLVED) return@processAllRangeMarkers true
      val startOffset = TextRangeScalarUtil.startOffset(range)
      val endOffset = TextRangeScalarUtil.endOffset(range)
      resolvedMarkers.add(marker)
      if (startOffset != entry.nodeStart + ancestorDelta || endOffset != entry.nodeEnd + ancestorDelta) {
        val entries = restoredEntries ?: ArrayList<PMarkerRoot.MarkerEntry>().also { restoredEntries = it }
        entries.add(entry.copy(nodeStart = startOffset, nodeEnd = endOffset))
      }
      true
    }

    val restoredRoot = restoredEntries?.let { entries ->
      sourceRoot.restoreEntries(entries)
    } ?: sourceRoot
    document.rangeMarkers.rootStore().restoreRoot(document.core.snapshot(), restoredRoot)
    attach(document)
    resolvedMarkers.forEach(SnapshotLazyRangeMarker::markInitialRangeResolved)
  }

  @Synchronized
  override fun documentChanged(event: DocumentEvent) {
    val document = event.document as? DocumentImpl ?: return
    rootReference = markerRoot(document)
  }

  companion object {
    private val FILE_MARKER_ROOT_REF_KEY: Key<Reference<VirtualFileMarkerRoot>> = Key.create("snapshot.range.marker.root")

    /** Returns the root for the file associated with [document], or `null` when the file or its document service is unavailable. */
    internal fun getOrCreate(document: DocumentImpl): VirtualFileMarkerRoot? {
      val fileDocumentManager = ApplicationManager.getApplication()?.getService(FileDocumentManager::class.java) ?: return null
      val file = fileDocumentManager.getFile(document) ?: return null
      return getOrCreate(file, document)
    }

    /** Reuses or creates the root for [file]. Checks for a cached document without loading it. */
    fun getOrCreate(file: VirtualFile): VirtualFileMarkerRoot {
      val document = FileDocumentManager.getInstance().getCachedDocument(file) as? DocumentImpl
      return getOrCreate(file, document)
    }

    private fun getOrCreate(file: VirtualFile, document: DocumentImpl?): VirtualFileMarkerRoot {
      val initialRootReference = document?.let(::markerRoot) ?: PMarkerRootReference(PMarkerRoot.empty(), null)

      while (true) {
        val oldReference = file.getUserData(FILE_MARKER_ROOT_REF_KEY)
        var fileRoot = oldReference?.get()
        if (fileRoot == null) {
          fileRoot = VirtualFileMarkerRoot(file, initialRootReference)
          if (!file.replace(FILE_MARKER_ROOT_REF_KEY, oldReference, WeakReference(fileRoot))) continue
        }
        if (document != null) fileRoot.attach(document)
        return fileRoot
      }
    }

    /** Transfers the retained marker root for [file] to [document], when such a root exists. */
    @JvmStatic
    fun restoreRangeMarkersFromFile(document: DocumentImpl, file: VirtualFile, tabSize: Int) {
      SnapshotMarkerReference.processQueue()
      val fileRoot = find(file) ?: return
      fileRoot.restoreAndAttach(document, tabSize)
    }

    /** Checks whether a live file root remains after processing collected markers. */
    @JvmStatic
    fun areRangeMarkersRetainedFor(file: VirtualFile): Boolean {
      SnapshotMarkerReference.processQueue()
      return find(file) != null
    }

    private fun find(file: VirtualFile): VirtualFileMarkerRoot? {
      val reference = file.getUserData(FILE_MARKER_ROOT_REF_KEY) ?: return null
      val fileRoot = reference.get()
      if (fileRoot == null) {
        file.replace(FILE_MARKER_ROOT_REF_KEY, reference, null)
      }
      return fileRoot
    }

    private fun markerRoot(document: DocumentImpl): PMarkerRootReference {
      val store = document.rangeMarkers.rootStore()
      return store.currentRootCache.get()?.reference ?: store.rootReference(document.core.snapshot())
    }
  }
}
