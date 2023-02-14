// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener.Companion.installAsyncVirtualFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*

abstract class NewFilesListener : VirtualFileChangesListener {

  abstract fun fireNewFilesCreated()

  private var isCreatedNewFiles = false

  override fun init() {
    isCreatedNewFiles = false
  }

  override fun apply() {
    if (isCreatedNewFiles) {
      fireNewFilesCreated()
    }
  }

  override fun isRelevant(file: VirtualFile, event: VFileEvent): Boolean {
    return event is VFileCopyEvent ||
           event is VFileCreateEvent ||
           event is VFileMoveEvent ||
           (event is VFilePropertyChangeEvent && event.isRename)
  }

  override fun updateFile(file: VirtualFile, event: VFileEvent) {
    isCreatedNewFiles = true
  }

  companion object {
    fun whenNewFilesCreated(action: () -> Unit, parentDisposable: Disposable) {
      val listener = object : NewFilesListener() {
        override fun fireNewFilesCreated() = action()
      }
      installAsyncVirtualFileListener(listener, parentDisposable)
    }
  }
}