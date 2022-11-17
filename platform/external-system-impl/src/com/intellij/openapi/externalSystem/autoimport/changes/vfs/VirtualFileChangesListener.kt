// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport.changes.vfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

interface VirtualFileChangesListener {

  fun isProcessRecursively(): Boolean = true

  fun init() {}

  fun apply() {}

  fun isRelevant(file: VirtualFile, event: VFileEvent): Boolean = false

  fun updateFile(file: VirtualFile, event: VFileEvent) {}

  companion object {

    fun installBulkVirtualFileListener(listener: VirtualFileChangesListener, parentDisposable: Disposable) {
      val bulkListener = object : BulkFileListener {
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
        .subscribe(VirtualFileManager.VFS_CHANGES, bulkListener)
    }

    fun installAsyncVirtualFileListener(listener: VirtualFileChangesListener, parentDisposable: Disposable) {
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
      virtualFileManager.addAsyncFileListener(asyncListener, parentDisposable)
    }
  }
}