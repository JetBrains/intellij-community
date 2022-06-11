// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType
import com.intellij.openapi.externalSystem.autoimport.settings.AsyncSupplier
import com.intellij.openapi.externalSystem.util.PathPrefixTreeMap
import com.intellij.openapi.vfs.VirtualFileManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Filters and delegates files and documents events into subscribed listeners.
 * Allows to use heavy paths filer, that is defined by [filesProvider].
 * Call sequences of [changesListener]'s functions will be skipped if change events didn't happen in watched files.
 */
class AsyncFilesChangesListener(
  private val filesProvider: AsyncSupplier<Set<String>>,
  private val changesListener: FilesChangesListener,
  private val parentDisposable: Disposable
) : FilesChangesListener {
  private val updatedFiles = ConcurrentHashMap<String, ModificationData>()

  override fun init() {
    updatedFiles.clear()
  }

  override fun onFileChange(path: String, modificationStamp: Long, modificationType: ExternalSystemModificationType) {
    updatedFiles[path] = ModificationData(modificationStamp, modificationType)
  }

  override fun apply() {
    val updatedFilesSnapshot = HashMap(updatedFiles)
    filesProvider.supply(
      { filesToWatch ->
        val index = PathPrefixTreeMap<Boolean>()
        filesToWatch.forEach { index[it] = true }
        val updatedWatchedFiles = updatedFilesSnapshot.flatMap { (path, modificationData) ->
          index.getAllDescendantKeys(path)
            .map { it to modificationData }
        }
        if (updatedWatchedFiles.isNotEmpty()) {
          changesListener.init()
          for ((path, modificationData) in updatedWatchedFiles) {
            val (modificationStamp, modificationType) = modificationData
            changesListener.onFileChange(path, modificationStamp, modificationType)
          }
          changesListener.apply()
        }
      }, parentDisposable)
  }

  private data class ModificationData(val modificationStamp: Long, val modificationType: ExternalSystemModificationType)

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
      val changesProvider = VirtualFilesChangesProvider(isIgnoreInternalChanges)
      val fileManager = VirtualFileManager.getInstance()
      fileManager.addAsyncFileListener(changesProvider, parentDisposable)
      val asyncListener = AsyncFilesChangesListener(filesProvider, listener, parentDisposable)
      changesProvider.subscribe(asyncListener, parentDisposable)
    }

    @JvmStatic
    fun subscribeOnDocumentsChanges(
      isIgnoreExternalChanges: Boolean,
      filesProvider: AsyncSupplier<Set<String>>,
      listener: FilesChangesListener,
      parentDisposable: Disposable
    ) {
      val changesProvider = DocumentsChangesProvider(isIgnoreExternalChanges)
      val eventMulticaster = EditorFactory.getInstance().eventMulticaster
      eventMulticaster.addDocumentListener(changesProvider, parentDisposable)
      val asyncListener = AsyncFilesChangesListener(filesProvider, listener, parentDisposable)
      changesProvider.subscribe(asyncListener, parentDisposable)
    }
  }
}