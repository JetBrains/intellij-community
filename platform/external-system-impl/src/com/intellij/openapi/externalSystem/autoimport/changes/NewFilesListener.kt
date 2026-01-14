// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener.Companion.installAsyncVirtualFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentLinkedQueue

@ApiStatus.Internal
abstract class NewFilesListener : VirtualFileChangesListener {

  protected abstract fun fireNewFilesCreated(files: Collection<VirtualFile>)
  private val modifiedFiles = ConcurrentLinkedQueue<VirtualFile>()

  override fun init() {
    modifiedFiles.clear()
  }

  override fun apply() {
    val set = ArrayList(modifiedFiles)
    if (set.isNotEmpty()) {
      fireNewFilesCreated(set)
    }
  }

  override fun isRelevant(file: VirtualFile, event: VFileEvent): Boolean {
    return event is VFileCopyEvent ||
           event is VFileCreateEvent ||
           event is VFileMoveEvent ||
           (event is VFilePropertyChangeEvent && event.isRename)
  }

  override fun updateFile(file: VirtualFile, event: VFileEvent) {
    modifiedFiles.add(file)
  }

  companion object {
    fun whenNewFilesCreated(action: (modifiedFiles: Collection<VirtualFile>) -> Unit, parentDisposable: Disposable) {
      val listener = object : NewFilesListener() {
        override fun fireNewFilesCreated(files: Collection<VirtualFile>) = action(files)
      }
      installAsyncVirtualFileListener(listener, parentDisposable)
    }
  }
}