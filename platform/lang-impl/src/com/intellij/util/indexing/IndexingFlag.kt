// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * An object dedicated to manage in memory `isIndexed` file flag.
 */
@ApiStatus.Internal
object IndexingFlag {
  private val hashes = StripedIndexingStampLock()

  @JvmStatic
  val nonExistentHash = StripedIndexingStampLock.NON_EXISTENT_HASH

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
      hashes.releaseHash(file.id)
      file.isFileIndexed = false
    }
  }

  @JvmStatic
  fun setFileIndexed(file: VirtualFile) {
    if (file is VirtualFileSystemEntry) {
      file.isFileIndexed = true
    }
  }

  @JvmStatic
  fun getOrCreateHash(file: VirtualFile): Long {
    if (file is VirtualFileSystemEntry) {
      return hashes.getHash(file.id)
    }
    return nonExistentHash
  }

  @JvmStatic
  fun unlockFile(file: VirtualFile) {
    if (file is VirtualFileSystemEntry) {
      hashes.releaseHash(file.id)
    }
  }

  @JvmStatic
  fun setIndexedIfFileWithSameLock(file: VirtualFile, lockObject: Long) {
    if (file is VirtualFileSystemEntry) {
      val hash = hashes.releaseHash(file.id)
      if (!file.isFileIndexed) {
        file.isFileIndexed = hash == lockObject
      }
    }
  }

  @JvmStatic
  fun unlockAllFiles() {
    hashes.clear()
  }

  @JvmStatic
  @TestOnly
  fun dumpLockedFiles(): IntArray = hashes.dumpIds()
}