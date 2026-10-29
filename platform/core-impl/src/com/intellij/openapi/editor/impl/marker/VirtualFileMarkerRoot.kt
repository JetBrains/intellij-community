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
import java.util.concurrent.atomic.AtomicReference

/**
 * Virtual file-backed marker state shared by marker handles and a live document without retaining either the document or its
 * text. A weak reference stored in the [VirtualFile] makes the state discoverable when a document is recreated.
 */
class VirtualFileMarkerRoot private constructor(
  internal val file: VirtualFile,
  initialRootReference: AtomicReference<PMarkerRoot>,
) : MarkerRootUpdater(), DocumentListener {
  @Volatile
  private var rootReference: AtomicReference<PMarkerRoot> = initialRootReference

  @Volatile
  private var documentReference: WeakReference<DocumentImpl>? = null

  override val document: DocumentImpl?
    get() = documentReference?.get()

  @Synchronized
  override fun createHandle(marker: SnapshotRangeMarkerImpl, retainStrong: Boolean): SnapshotMarkerReference<*> =
    rootReference.get().createHandle(marker, retainStrong)

  override fun currentRootReference(): AtomicReference<PMarkerRoot> {
    val observedDocumentReference = documentReference ?: return rootReference
    val document = observedDocumentReference.get() ?: return rootReference
    val currentRootReference = markerRoot(document)
    if (documentReference === observedDocumentReference) {
      rootReference = currentRootReference
    }
    return currentRootReference
  }

  @Synchronized
  override fun updateRootAtomically(
    rootReference: AtomicReference<PMarkerRoot>?, // null means currentRootReference()
    update: (PMarkerRoot) -> PMarkerRoot,
  ): PMarkerRoot? {
    val document = if (rootReference == null) document else null
    if (document == null) return super.updateRootAtomically(rootReference, update)
    try {
      return document.rangeMarkers.rootStore().updateRootAtomically(null, update)
    }
    finally {
      this.rootReference = markerRoot(document)
      ObjectUtilsRt.reachabilityFence(document)
    }
  }

  @Synchronized
  override fun purgeCollected(handles: Collection<SnapshotMarkerReference<*>>): Boolean = purgeCollected(document, handles)

  @Synchronized
  private fun attach(document: DocumentImpl) {
    rootReference = markerRoot(document)
    if (documentReference?.get() !== document) {
      document.addDocumentListener(this)
      documentReference = WeakReference(document)
    }
  }

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

    internal fun getOrCreate(document: DocumentImpl): VirtualFileMarkerRoot? {
      val fileDocumentManager = ApplicationManager.getApplication()?.getService(FileDocumentManager::class.java) ?: return null
      val file = fileDocumentManager.getFile(document) ?: return null
      return getOrCreate(file, document)
    }

    internal fun getOrCreate(file: VirtualFile): VirtualFileMarkerRoot {
      val document = FileDocumentManager.getInstance().getCachedDocument(file) as? DocumentImpl
      return getOrCreate(file, document)
    }

    private fun getOrCreate(file: VirtualFile, document: DocumentImpl?): VirtualFileMarkerRoot {
      val initialRootReference = document?.let(::markerRoot) ?: AtomicReference(PMarkerRoot.empty())

      while (true) {
        val oldReference = file.getUserData(FILE_MARKER_ROOT_REF_KEY)
        oldReference?.get()?.let { fileRoot ->
          if (document != null) fileRoot.attach(document)
          return fileRoot
        }

        val fileRoot = VirtualFileMarkerRoot(file, initialRootReference)
        val newReference = WeakReference(fileRoot)
        if (file.replace(FILE_MARKER_ROOT_REF_KEY, oldReference, newReference)) {
          if (document != null) fileRoot.attach(document)
          return fileRoot
        }
      }
    }

    @JvmStatic
    fun restoreRangeMarkersFromFile(document: DocumentImpl, file: VirtualFile, tabSize: Int) {
      SnapshotMarkerEngineImpl.processQueue()
      val fileRoot = find(file) ?: return
      fileRoot.restoreAndAttach(document, tabSize)
    }

    @JvmStatic
    fun areRangeMarkersRetainedFor(file: VirtualFile): Boolean {
      SnapshotMarkerEngineImpl.processQueue()
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

    private fun markerRoot(document: DocumentImpl): AtomicReference<PMarkerRoot> =
      document.rangeMarkers.rootStore().rootReference(document.core.snapshot())
  }
}
