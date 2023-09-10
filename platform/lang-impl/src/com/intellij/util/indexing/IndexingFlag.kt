// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.impl.VfsData
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicInteger

/**
 * An object dedicated to manage in memory `isIndexed` file flag.
 */
@ApiStatus.Internal
object IndexingFlag {

  private const val NULL_INDEXING_STAMP: Int = 0

  private val indexingStamp = AtomicInteger(1)

  private val hashes = StripedIndexingStampLock()

  @JvmStatic
  val nonExistentHash: Long = StripedIndexingStampLock.NON_EXISTENT_HASH

  @JvmStatic
  fun cleanupProcessedFlag() {
    indexingStamp.updateAndGet { cur ->
      val next: Int = cur + 1
      if (next == NULL_INDEXING_STAMP) NULL_INDEXING_STAMP + 1 else next
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
      file.indexedStamp = NULL_INDEXING_STAMP
    }
  }

  @JvmStatic
  fun setFileIndexed(file: VirtualFile) {
    if (file is VirtualFileSystemEntry) {
      file.indexedStamp = indexingStamp.get()
    }
  }

  @JvmStatic
  fun isFileIndexed(file: VirtualFile): Boolean {
    if (VfsData.isIsIndexedFlagDisabled()) {
      return false;
    }
    return file is VirtualFileSystemEntry && file.indexedStamp == indexingStamp.get()
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
  fun setIndexedIfFileWithSameLock(file: VirtualFile, lockObject: Long) {
    if (file is VirtualFileSystemEntry) {
      val hash = hashes.releaseHash(file.id)
      if (file.indexedStamp != indexingStamp.get()) {
        file.indexedStamp = if (hash == lockObject) indexingStamp.get() else NULL_INDEXING_STAMP
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