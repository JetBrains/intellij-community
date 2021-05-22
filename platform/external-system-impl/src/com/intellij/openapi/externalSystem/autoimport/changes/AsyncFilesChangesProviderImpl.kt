// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.externalSystem.autoimport.settings.AsyncOperation
import com.intellij.openapi.vfs.VirtualFileManager


class AsyncFilesChangesProviderImpl(private val filesProvider: AsyncOperation<Set<String>>) : FilesChangesProvider {
  override fun subscribe(listener: FilesChangesListener, parentDisposable: Disposable) {
    subscribeAsAsyncVirtualFilesChangesProvider(true, listener, parentDisposable)
    subscribeAsAsyncDocumentChangesProvider(listener, parentDisposable)
  }

  fun subscribeAsAsyncVirtualFilesChangesProvider(
    isIgnoreUpdatesFromSave: Boolean,
    listener: FilesChangesListener,
    parentDisposable: Disposable
  ) {
    val changesProvider = VirtualFilesChangesProvider(isIgnoreUpdatesFromSave)
    val fileManager = VirtualFileManager.getInstance()
    fileManager.addAsyncFileListener(changesProvider, parentDisposable)

    changesProvider.subscribeAsAsync(listener, parentDisposable)
  }

  private fun subscribeAsAsyncDocumentChangesProvider(listener: FilesChangesListener, parentDisposable: Disposable) {
    val changesProvider = DocumentsChangesProvider()
    val eventMulticaster = EditorFactory.getInstance().eventMulticaster
    eventMulticaster.addDocumentListener(changesProvider, parentDisposable)

    changesProvider.subscribeAsAsync(listener, parentDisposable)
  }

  private fun FilesChangesProvider.subscribeAsAsync(listener: FilesChangesListener, parentDisposable: Disposable) {
    val asyncFilesChangesProvider = AsyncFilesChangesProviderBase(filesProvider, parentDisposable)
    asyncFilesChangesProvider.subscribe(listener, parentDisposable)

    subscribe(asyncFilesChangesProvider, parentDisposable)
  }
}