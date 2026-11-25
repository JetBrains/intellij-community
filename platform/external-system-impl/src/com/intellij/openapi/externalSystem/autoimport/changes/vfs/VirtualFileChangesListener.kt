// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport.changes.vfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface VirtualFileChangesListener {

  fun isProcessRecursively(): Boolean = false

  fun init() {}

  fun apply() {}

  fun isRelevant(file: VirtualFile, event: VFileEvent): Boolean = false

  fun updateFile(file: VirtualFile, event: VFileEvent) {}

  companion object {

    /**
     * Subscribes [listener] to [VirtualFileManager.VFS_CHANGES_BG].
     * The [listener] runs on a background thread under write all its methods.
     */
    fun installBulkVirtualFileListener(listener: VirtualFileChangesListener, parentDisposable: Disposable) {
      val bulkListener = object : BulkFileListenerBackgroundable {
        override fun before(events: List<VFileEvent>) {
          val separator = VirtualFileChangesSeparator(listener, events)
          listener.init()
          separator.processBeforeEvents()
          listener.apply()
        }

        override fun after(events: List<VFileEvent>) {
          val separator = VirtualFileChangesSeparator(listener, events)
          listener.init()
          separator.processAfterEvents()
          listener.apply()
        }
      }
      val application = ApplicationManager.getApplication()
      application.messageBus.connect(parentDisposable)
        .subscribe(VirtualFileManager.VFS_CHANGES_BG, bulkListener)
    }

    /**
     * Registers [listener] with [VirtualFileManager.addAsyncFileListenerBackgroundable].
     * The [listener] runs all its methods on a background thread under the write lock.
     */
    fun installAsyncVirtualFileListener(listener: VirtualFileChangesListener, parentDisposable: Disposable) {
      doInstallAsyncVirtualFileListener(listener, parentDisposable, useEdt = false)
    }

    /**
     * If [useEdt] is true, registers [listener] with [VirtualFileManager.addAsyncFileListener].
     * Then the [listener] runs on the EDT.
     */
    @Deprecated("The EDT mode exists only for compatibility. Use the overload without useEdt.")
    fun installAsyncVirtualFileListener(listener: VirtualFileChangesListener, parentDisposable: Disposable, useEdt: Boolean) {
      doInstallAsyncVirtualFileListener(listener, parentDisposable, useEdt)
    }

    private fun doInstallAsyncVirtualFileListener(listener: VirtualFileChangesListener, parentDisposable: Disposable, useEdt: Boolean) {
      val asyncListener = AsyncFileListener { events ->
        val separator = VirtualFileChangesSeparator(listener, events)
        object : AsyncFileListener.ChangeApplier {
          override fun beforeVfsChange() {
            listener.init()
            separator.processBeforeEvents()
          }

          override fun afterVfsChange() {
            separator.processAfterEvents()
            listener.apply()
          }
        }
      }
      val virtualFileManager = VirtualFileManager.getInstance()
      if (!useEdt) {
        virtualFileManager.addAsyncFileListenerBackgroundable(asyncListener, parentDisposable)
      }
      else {
        virtualFileManager.addAsyncFileListener(asyncListener, parentDisposable)
      }
    }
  }
}