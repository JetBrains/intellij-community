// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.util.application
import com.intellij.util.indexing.dependencies.*
import com.intellij.util.indexing.impl.perFileVersion.LongFileAttribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.concurrent.Volatile

/**
 * An object dedicated to manage persistent `isIndexed` file flag.
 */
@ApiStatus.Internal
object IndexingFlag {
  private val attribute = FileAttribute("indexing.flag", 1, true)

  @Volatile
  private var persistence = LongFileAttribute.overFastAttribute(attribute)
  private val hashes = StripedIndexingStampLock()

  @JvmStatic
  val nonExistentHash: Long = StripedIndexingStampLock.NON_EXISTENT_HASH

  @JvmStatic
  fun cleanupProcessedFlag(debugReason: String) {
    application.service<AppIndexingDependenciesService>().invalidateAllStamps(debugReason)
  }

  private fun VirtualFile.asApplicable(): VirtualFileWithId? {
    return this as? VirtualFileWithId
  }

  @JvmStatic
  fun cleanProcessedFlagRecursively(file: VirtualFile) {
    val fileWithId = file.asApplicable() ?: return
    cleanProcessingFlag(file)
    if (fileWithId !is VirtualFileSystemEntry) return
    if (!fileWithId.isDirectory()) return
    for (child in fileWithId.cachedChildren) {
      cleanProcessedFlagRecursively(child)
    }
  }

  @JvmStatic
  fun cleanProcessingFlag(file: VirtualFile) {
    setFileIndexed(file, ProjectIndexingDependenciesService.NULL_STAMP)
  }

  @JvmStatic
  fun cleanProcessingFlag(fileId: Int) {
    // the file might have already been deleted, so there might be no VirtualFile for given fileId
    setFileIndexed(fileId, ProjectIndexingDependenciesService.NULL_STAMP)
  }

  @JvmStatic
  fun setFileIndexed(file: VirtualFile, stamp: FileIndexingStamp) {
    val fileWithId = file.asApplicable() ?: return
    setFileIndexed(fileWithId.id, stamp)
  }

  private fun setFileIndexed(fileId: Int, stamp: FileIndexingStamp) {
    stamp.store { s ->
      persistence.writeLong(fileId, s)
    }
  }

  @JvmStatic
  fun isFileIndexed(file: VirtualFile, stamp: FileIndexingStamp): Boolean {
    val fileWithId = file.asApplicable() ?: return false
    return stamp.isSame(persistence.readLong(fileWithId.id))
  }

  @JvmStatic
  fun isFileChanged(file: VirtualFile, stamp: FileIndexingStamp): IsFileChangedResult {
    val fileWithId = file.asApplicable() ?: return IsFileChangedResult.UNKNOWN
    return stamp.isFileChanged(persistence.readLong(fileWithId.id).toFileModCount())
  }

  @JvmStatic
  fun getOrCreateHash(file: VirtualFile): Long {
    val fileWithId = file.asApplicable() ?: return nonExistentHash
    return hashes.getHash(fileWithId.id)
  }

  @JvmStatic
  fun unlockFile(file: VirtualFile) {
    val fileWithId = file.asApplicable() ?: return
    hashes.releaseHash(fileWithId.id)
  }

  @JvmStatic
  fun setIndexedIfFileWithSameLock(file: VirtualFile, lockObject: Long, stamp: FileIndexingStamp) {
    val fileWithId = file.asApplicable() ?: return
    val hash = hashes.releaseHash(fileWithId.id)
    if (isFileIndexed(file, stamp)) return

    if (hash == lockObject) {
      setFileIndexed(file, stamp)
    }
    else {
      cleanProcessingFlag(file)
    }
  }

  @JvmStatic
  fun unlockAllFiles() {
    hashes.clear()
  }

  fun reloadAttributes() {
    persistence = LongFileAttribute.overFastAttribute(attribute)
  }

  @JvmStatic
  @TestOnly
  fun dumpLockedFiles(): IntArray = hashes.dumpIds()
}