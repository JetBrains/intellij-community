// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.annotations.ApiStatus

abstract class RefreshSession {
  abstract fun addFile(file: VirtualFile)

  abstract fun addAllFiles(files: Collection<VirtualFile>)

  fun addAllFiles(vararg files: VirtualFile) {
    addAllFiles(listOf(*files))
  }

  /**
   * Scan those children if they are not cached in VFS and recursively preload their children.
   *
   * Do nothing for files already loaded into vfs.
   *
   * @see [com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl.refreshNioFilesInternal]
   * @see [com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl.createChildAndFireCreationEvent]
   */
  @ApiStatus.Internal
  abstract fun addNewChildren(parent: VirtualFile, childrenNames: Collection<String>)

  @ApiStatus.Internal
  abstract fun addCopyFile(newParent: VirtualFile, newName: String, file: VirtualFile, requestor: Any?)

  abstract fun launch()

  @ApiStatus.Internal
  abstract suspend fun executeInBackgroundWriteAction(highPriority: Boolean)

  @ApiStatus.Internal
  abstract fun addEvents(events: List<VFileEvent>)

  abstract fun cancel()
}
