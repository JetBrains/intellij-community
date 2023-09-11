// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.impl.VfsData
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.util.application
import com.intellij.util.indexing.dependencies.FileIndexingStampService
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * An object dedicated to manage in memory `isIndexed` file flag.
 */
@ApiStatus.Internal
object IndexingFlag {
  private val hashes = StripedIndexingStampLock()

  @JvmStatic
  val nonExistentHash: Long = StripedIndexingStampLock.NON_EXISTENT_HASH

  @JvmStatic
  fun cleanupProcessedFlag() {
    application.service<FileIndexingStampService>().invalidateAllStamps()
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
      file.indexedStamp = FileIndexingStampService.NULL_STAMP.toInt()
    }
  }

  @JvmStatic
  fun setFileIndexed(file: VirtualFile, stamp: FileIndexingStampService.FileIndexingStamp) {
    if (file is VirtualFileSystemEntry) {
      file.indexedStamp = stamp.toInt()
    }
  }

  @JvmStatic
  fun isFileIndexed(file: VirtualFile, stamp: FileIndexingStampService.FileIndexingStamp): Boolean {
    if (VfsData.isIsIndexedFlagDisabled()) {
      return false;
    }
    return file is VirtualFileSystemEntry && file.indexedStamp == stamp.toInt()
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
    if (file is VirtualFileWithId) {
      hashes.releaseHash(file.id)
    }
  }

  @JvmStatic
  fun setIndexedIfFileWithSameLock(file: VirtualFile, lockObject: Long, stamp: FileIndexingStampService.FileIndexingStamp) {
    if (file is VirtualFileSystemEntry) {
      val hash = hashes.releaseHash(file.id)
      if (file.indexedStamp != stamp.toInt()) {
        file.indexedStamp = (if (hash == lockObject) stamp else FileIndexingStampService.NULL_STAMP).toInt()
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