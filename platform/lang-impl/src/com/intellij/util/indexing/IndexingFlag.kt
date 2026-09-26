// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.util.application
import com.intellij.util.indexing.dependencies.AppIndexingDependenciesService
import com.intellij.util.indexing.dependencies.FileIndexingStamp
import com.intellij.util.indexing.dependencies.IsFileChangedResult
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import com.intellij.util.indexing.impl.perFileVersion.LongFileAttribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * An object dedicated to manage persistent `isIndexed` file flag.
 *
 * For each file it saves the file mod count and [AppIndexingDependenciesService.current] at the moment when the file was indexed.
 * The IndexingFlag is used to quickly check if all indexes for a file are up to date (see IJPL-229).
 * But if IndexingFlag is not up to date, it doesn't necessarily mean that indexes for a file are outdated.
 *
 * IndexingFlag can also be used to check the dirty files from the previous IDE sessions for which [IndexingStamp] may not have been updated
 * or the change was not persisted to disk.
 *
 * The alternative is [IndexingStamp] which contains information per-index but can become outdated (see the doc) and is slower.
 * So in practice the combination of the two should be used (see [FileBasedIndexImpl.getIndexingState]).
 */
@ApiStatus.Internal
object IndexingFlag {
  private val attribute = FileAttribute("indexing.flag", 1, true)

  /** fileId -> ([FileIndexingStamp] as int64) */
  private val persistence = LongFileAttribute.overFastAttribute(attribute)
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
      //TODO RC: shouldn't we use .iterInDbChildren()? Because it could be the child was loaded at some point, left it's
      //         mark in IndexingFlag, but then the parent GCed from the VFS cache, and reloaded later, with fewer
      //         children in-memory
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
    //TODO RC: sometimes incorrect fileIds (>maxAllocatedFileId) are coming here. Probably, it is not that incorrect to
    //         clean incorrect fileId? Maybe we should just ignore incorrect fileId (because they are effectively already
    //         'cleaned' in some sense) instead of throwing an exception?
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

  /**
   * Possible situations:
   * - Regular situation when we can trust [IndexingStamp] which tells us if any given index is up to date for any given file.
   *   [IsFileChangedResult.UNKNOWN] is returned and then later in [FileBasedIndexImpl.getIndexingState] the actual [IndexingStamp] is checked.
   *
   * - Situation when we cannot trust [IndexingStamp].
   *   This situation occurs if we lost the whole list of dirty files from the previous session (see [DirtyFileIdsWereMissed]).
   *   In this case the result from [stamp] ([IsFileChangedResult.YES] or [IsFileChangedResult.NO]) is returned.
   *   I.e., the file will be either considered fully indexed if file mod count AND IDE configuration didn't change.
   *   Or it'll be considered fully unindexed if the file OR IDE configuration is changed.
   *   It also means that if IDE configuration changed, and we lost the list of dirty files, then we'll re-index all the files.
   */
  @JvmStatic
  fun isFileChanged(file: VirtualFile, stamp: FileIndexingStamp): IsFileChangedResult {
    val fileWithId = file.asApplicable() ?: return IsFileChangedResult.UNKNOWN
    return stamp.isFileChanged(persistence.readLong(fileWithId.id))
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
    persistence.close()//will be reopened on next access
  }

  @JvmStatic
  fun close(){
    unlockAllFiles()
    persistence.close()
  }

  @JvmStatic
  @TestOnly
  fun dumpLockedFiles(): IntArray = hashes.dumpIds()
}