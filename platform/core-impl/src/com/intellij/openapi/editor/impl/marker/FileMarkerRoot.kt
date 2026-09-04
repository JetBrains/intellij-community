// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.DocumentSnapshotImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * File-backed marker state shared by marker handles and a live document without retaining either the document or its
 * text. A weak reference stored in the [VirtualFile] makes the state discoverable when a document is recreated.
 */
internal class FileMarkerRoot private constructor(
  internal val file: VirtualFile,
  initialRootReference: AtomicReference<PMarkerRoot>,
) : DocumentListener {
  @Volatile
  private var rootReference: AtomicReference<PMarkerRoot> = initialRootReference

  @Volatile
  private var documentReference: WeakReference<DocumentImpl>? = null

  internal fun rootReference(): AtomicReference<PMarkerRoot> {
    val document = documentReference?.get() ?: return rootReference
    val currentRootReference = markerRoot(document)
    rootReference = currentRootReference
    return currentRootReference
  }

  private fun attach(document: DocumentImpl) {
    synchronized(this) {
      rootReference = markerRoot(document)
      if (documentReference?.get() !== document) {
        document.addDocumentListener(this)
        documentReference = WeakReference(document)
      }
    }
  }

  private fun restoreAndAttach(document: DocumentImpl, tabSize: Int) {
    synchronized(this) {
      val sourceRoot = rootReference.get()
      var restoredRoot = sourceRoot
      val resolvedMarkers = ArrayList<SnapshotLazyRangeMarker>()
      sourceRoot.processRangeMarkersOverlappingWith(0, Int.MAX_VALUE, 0) { entry ->
        val marker = entry.markerReference?.get() as? SnapshotLazyRangeMarker ?: return@processRangeMarkersOverlappingWith true
        val range = marker.initialRange(document, tabSize) ?: return@processRangeMarkersOverlappingWith true
        resolvedMarkers.add(marker)
        if (range.startOffset != entry.startOffset || range.endOffset != entry.endOffset) {
          restoredRoot = restoredRoot.remove(entry.markerId).insert(
            entry.markerId,
            range.startOffset,
            range.endOffset,
            entry.spec,
            entry.flavorFlags,
            entry.markerReference,
            entry.measure,
          )
        }
        true
      }

      markerRoot(document).set(restoredRoot)
      attach(document)
      resolvedMarkers.forEach(SnapshotLazyRangeMarker::markInitialRangeResolved)
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    val document = event.document as? DocumentImpl ?: return
    rootReference = markerRoot(document)
  }

  companion object {
    private val FILE_MARKER_ROOT_REF_KEY: Key<Reference<FileMarkerRoot>> = Key.create("snapshot.range.marker.root")

    internal fun getOrCreate(document: DocumentImpl): FileMarkerRoot? {
      val fileDocumentManager = ApplicationManager.getApplication()?.getService(FileDocumentManager::class.java) ?: return null
      val file = fileDocumentManager.getFile(document) ?: return null
      return getOrCreate(file, document)
    }

    internal fun getOrCreate(file: VirtualFile): FileMarkerRoot {
      val document = FileDocumentManager.getInstance().getCachedDocument(file) as? DocumentImpl
      return getOrCreate(file, document)
    }

    private fun getOrCreate(file: VirtualFile, document: DocumentImpl?): FileMarkerRoot {
      val initialRootReference = document?.let(::markerRoot) ?: AtomicReference<PMarkerRoot>(PMarkerRootImpl.empty())

      while (true) {
        val oldReference = file.getUserData(FILE_MARKER_ROOT_REF_KEY)
        oldReference?.get()?.let { fileRoot ->
          if (document != null) fileRoot.attach(document)
          return fileRoot
        }

        val fileRoot = FileMarkerRoot(file, initialRootReference)
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

    private fun find(file: VirtualFile): FileMarkerRoot? {
      val reference = file.getUserData(FILE_MARKER_ROOT_REF_KEY) ?: return null
      val fileRoot = reference.get()
      if (fileRoot == null) {
        file.replace(FILE_MARKER_ROOT_REF_KEY, reference, null)
      }
      return fileRoot
    }

    private fun markerRoot(document: DocumentImpl): AtomicReference<PMarkerRoot> =
      (document.core.snapshot() as DocumentSnapshotImpl).markerRoot
  }
}
