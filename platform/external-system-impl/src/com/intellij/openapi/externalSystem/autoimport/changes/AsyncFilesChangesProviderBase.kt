// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ModificationType
import com.intellij.openapi.externalSystem.autoimport.settings.AsyncOperation
import com.intellij.openapi.externalSystem.autoimport.settings.EdtAsyncOperation.Companion.invokeOnEdt
import com.intellij.openapi.externalSystem.util.PathPrefixTreeMap
import com.intellij.util.EventDispatcher

class AsyncFilesChangesProviderBase(
  private val filesProvider: AsyncOperation<Set<String>>,
  private val parentDisposable: Disposable
) : AsyncFilesChangesProvider {
  private val eventDispatcher = EventDispatcher.create(FilesChangesListener::class.java)

  override fun subscribe(listener: FilesChangesListener, parentDisposable: Disposable) {
    eventDispatcher.addListener(listener, parentDisposable)
  }

  private val updatedFiles = HashMap<String, ModificationData>()

  override fun init() {}

  override fun onFileChange(path: String, modificationStamp: Long, modificationType: ModificationType) {
    updatedFiles[path] = ModificationData(modificationStamp, modificationType)
  }

  override fun apply() {
    filesProvider.submit({ filesToWatch ->
      invokeOnEdt(filesProvider::isBlocking, {
        val index = PathPrefixTreeMap<Boolean>()
        filesToWatch.forEach { index[it] = true }
        eventDispatcher.multicaster.init()
        for ((path, modificationData) in updatedFiles) {
          val (modificationStamp, modificationType) = modificationData
          for (relevantPath in index.getAllAncestorKeys(path)) {
            eventDispatcher.multicaster.onFileChange(relevantPath, modificationStamp, modificationType)
          }
        }
        eventDispatcher.multicaster.apply()
        updatedFiles.clear()
      }, parentDisposable)
    }, parentDisposable)
  }

  private data class ModificationData(val modificationStamp: Long, val modificationType: ModificationType)
}