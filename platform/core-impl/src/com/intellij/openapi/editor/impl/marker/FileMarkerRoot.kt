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
      if (documentReference?.get() === document) return
      document.addDocumentListener(this)
      documentReference = WeakReference(document)
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    val document = event.document as? DocumentImpl ?: return
    rootReference = markerRoot(document)
  }

  companion object {
    private val FILE_MARKER_ROOT_REF_KEY: Key<Reference<FileMarkerRoot>> = Key.create("snapshot.range.marker.root")

    internal fun getOrCreate(document: DocumentImpl): FileMarkerRoot? {
      val application = ApplicationManager.getApplication() ?: return null
      val fileDocumentManager = application.getServiceIfCreated(FileDocumentManager::class.java) ?: return null
      val file = fileDocumentManager.getFile(document) ?: return null
      val initialRootReference = markerRoot(document)

      while (true) {
        val oldReference = file.getUserData(FILE_MARKER_ROOT_REF_KEY)
        oldReference?.get()?.let { fileRoot ->
          fileRoot.attach(document)
          return fileRoot
        }

        val fileRoot = FileMarkerRoot(file, initialRootReference)
        val newReference = WeakReference(fileRoot)
        if (file.replace(FILE_MARKER_ROOT_REF_KEY, oldReference, newReference)) {
          fileRoot.attach(document)
          return fileRoot
        }
      }
    }

    @JvmStatic
    fun restoreRangeMarkersFromFile(document: DocumentImpl, file: VirtualFile) {
      SnapshotMarkerEngineImpl.processQueue()
      val fileRoot = find(file) ?: return
      markerRoot(document).set(fileRoot.rootReference().get())
      fileRoot.attach(document)
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
