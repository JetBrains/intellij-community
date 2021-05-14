// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import org.jetbrains.annotations.ApiStatus

/**
 * An object dedicated to manage in memory `isIndexed` file flag.
 */
@ApiStatus.Internal
object IndexingFlag {
  @JvmStatic
  fun cleanupProcessedFlag() {
    val roots = ManagingFS.getInstance().roots
    for (root in roots) {
      cleanProcessedFlagRecursively(root)
    }
  }

  @JvmStatic
  fun cleanProcessedFlagRecursively(file: VirtualFile) {
    if (file !is VirtualFileSystemEntry) return
    cleanProcessingFlag(file)
    if (file.isDirectory()) {
      for (child in file.cachedChildren) {
        cleanProcessedFlagRecursively(child)
      }
    }
  }

  @JvmStatic
  fun cleanProcessingFlag(file: VirtualFile) {
    if (file is VirtualFileSystemEntry) {
      file.isFileIndexed = false
    }
  }

  @JvmStatic
  fun setFileIndexed(file: VirtualFile) {
    if (file is VirtualFileSystemEntry) {
      file.isFileIndexed = true
    }
  }
}