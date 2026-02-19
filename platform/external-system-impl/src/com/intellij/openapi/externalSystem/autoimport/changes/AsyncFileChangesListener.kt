// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.Stamp
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener.Companion.installAsyncVirtualFileListener
import com.intellij.openapi.externalSystem.autoimport.settings.AsyncSupplier
import com.intellij.openapi.util.io.CanonicalPathPrefixTree
import com.intellij.util.containers.prefixTree.set.toPrefixTreeSet
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Filters and delegates file and document events into subscribed listeners.
 * Allows using heavy paths filter, that is defined by [filesProvider].
 * The [changesListener]'s execution will be skipped if change events didn't happen in watched files.
 */
@ApiStatus.Internal
class AsyncFileChangesListener(
  private val filesProvider: AsyncSupplier<Set<String>>,
  private val changesListener: FilesChangesListener,
  private val parentDisposable: Disposable,
) {

  private val updatedFiles = ConcurrentHashMap<String, ModificationData>()

  fun init() {
    updatedFiles.clear()
  }

  fun onFileChange(path: String, modificationStamp: Long, modificationType: ExternalSystemModificationType) {
    updatedFiles[path] = ModificationData(modificationStamp, modificationType)
  }

  fun apply() {
    val stamp = Stamp.nextStamp()
    val updatedFilesSnapshot = HashMap(updatedFiles)
    filesProvider.supply(parentDisposable) { filesToWatch ->
      val index = filesToWatch.toPrefixTreeSet(CanonicalPathPrefixTree)
      val updatedWatchedFiles = updatedFilesSnapshot.flatMap { (path, modificationData) ->
        index.getDescendants(path)
          .map { it to modificationData }
      }
      if (updatedWatchedFiles.isNotEmpty()) {
        changesListener.init()
        for ((path, modificationData) in updatedWatchedFiles) {
          val (modificationStamp, modificationType) = modificationData
          changesListener.onFileChange(stamp, path, modificationStamp, modificationType)
        }
        changesListener.apply()
      }
    }
  }

  private data class ModificationData(
    val modificationStamp: Long,
    val modificationType: ExternalSystemModificationType,
  )

  companion object {
    @JvmStatic
    fun subscribeOnDocumentsAndVirtualFilesChanges(
      filesProvider: AsyncSupplier<Set<String>>,
      listener: FilesChangesListener,
      parentDisposable: Disposable
    ) {
      subscribeOnVirtualFilesChanges(true, filesProvider, listener, parentDisposable)
      subscribeOnDocumentsChanges(true, filesProvider, listener, parentDisposable)
    }

    @JvmStatic
    fun subscribeOnVirtualFilesChanges(
      isIgnoreInternalChanges: Boolean,
      filesProvider: AsyncSupplier<Set<String>>,
      listener: FilesChangesListener,
      parentDisposable: Disposable
    ) {
      val fileListener = AsyncFileChangesListener(filesProvider, listener, parentDisposable)
      val virtualFileListener = AsyncVirtualFilesChangesListener(isIgnoreInternalChanges, fileListener)
      installAsyncVirtualFileListener(virtualFileListener, parentDisposable)
    }

    @JvmStatic
    fun subscribeOnDocumentsChanges(
      isIgnoreExternalChanges: Boolean,
      filesProvider: AsyncSupplier<Set<String>>,
      listener: FilesChangesListener,
      parentDisposable: Disposable
    ) {
      val fileListener = AsyncFileChangesListener(filesProvider, listener, parentDisposable)
      val documentListener = AsyncDocumentChangesListener(isIgnoreExternalChanges, fileListener)
      EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, parentDisposable)
    }
  }
}