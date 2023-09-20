// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsData
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.util.application
import com.intellij.util.asSafely
import com.intellij.util.indexing.dependencies.AppIndexingDependenciesService
import com.intellij.util.indexing.dependencies.FileIndexingStamp
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
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
    application.service<AppIndexingDependenciesService>().invalidateAllStamps()
  }

  private fun VirtualFile.asApplicable(): VirtualFileSystemEntry? {
    return asSafely<VirtualFileSystemEntry>()?.let { if (VfsData.isIsIndexedFlagDisabled()) null else it }
  }

  @JvmStatic
  fun cleanProcessedFlagRecursively(file: VirtualFile) {
    file.asApplicable()?.also { entry ->
      cleanProcessingFlag(entry)
      if (entry.isDirectory()) {
        for (child in entry.cachedChildren) {
          cleanProcessedFlagRecursively(child)
        }
      }
    }
  }

  @JvmStatic
  fun cleanProcessingFlag(file: VirtualFile) {
    file.asApplicable()?.also { entry ->
      hashes.releaseHash(entry.id)
      ProjectIndexingDependenciesService.NULL_STAMP.store(entry::setIndexedStamp)
    }
  }

  @JvmStatic
  fun setFileIndexed(file: VirtualFile, stamp: FileIndexingStamp) {
    file.asApplicable()?.also { entry -> stamp.store(entry::setIndexedStamp) }
  }

  @JvmStatic
  fun isFileIndexed(file: VirtualFile, stamp: FileIndexingStamp): Boolean {
    return file.asApplicable()?.let { entry -> stamp.isSame(entry.indexedStamp) } ?: false
  }

  @JvmStatic
  fun getOrCreateHash(file: VirtualFile): Long {
    return file.asApplicable()?.let { entry -> hashes.getHash(entry.id) } ?: nonExistentHash
  }

  @JvmStatic
  fun unlockFile(file: VirtualFile) {
    file.asApplicable()?.also { entry -> hashes.releaseHash(entry.id) }
  }

  @JvmStatic
  fun setIndexedIfFileWithSameLock(file: VirtualFile, lockObject: Long, stamp: FileIndexingStamp) {
    file.asApplicable()?.also { entry ->
      val hash = hashes.releaseHash(entry.id)
      if (!stamp.isSame(entry.indexedStamp)) {
        if (hash == lockObject) {
          stamp.store(entry::setIndexedStamp)
        }
        else {
          ProjectIndexingDependenciesService.NULL_STAMP.store(entry::setIndexedStamp)
        }
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