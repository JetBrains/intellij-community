// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.testFramework.TestModeFlags
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

  @JvmStatic
  fun isIndexedFlagDisabled(): Boolean = isIndexedFlagDisabled(ApplicationManager.getApplication())

  @TestOnly
  @JvmStatic
  private val ENABLE_IS_INDEXED_FLAG_KEY: Key<Boolean> = Key("is_indexed_flag_enabled")

  @Volatile
  @JvmStatic
  private var indexedFlagDisabled: Boolean? = null

  @JvmStatic
  private fun isIndexedFlagDisabled(app: Application): Boolean {
    if (indexedFlagDisabled == null) {
      if (app.isUnitTestMode) {
        val enableByTestModeFlags = TestModeFlags.get(ENABLE_IS_INDEXED_FLAG_KEY)
        if (enableByTestModeFlags != null) {
          indexedFlagDisabled = !enableByTestModeFlags
          return !enableByTestModeFlags
        }
      }
      indexedFlagDisabled = Registry.`is`("indexing.disable.virtual.file.system.entry.is.file.indexed", false)
    }
    return indexedFlagDisabled!!
  }

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
    if (this is VirtualFileWithId && !isIndexedFlagDisabled()) {
      return this
    }
    else {
      return null
    }
  }

  @JvmStatic
  fun cleanProcessedFlagRecursively(file: VirtualFile) {
    file.asApplicable()?.also { fileWithId ->
      cleanProcessingFlag(file)
      if (fileWithId is VirtualFileSystemEntry) {
        if (fileWithId.isDirectory()) {
          for (child in fileWithId.cachedChildren) {
            cleanProcessedFlagRecursively(child)
          }
        }
      }
    }
  }

  @JvmStatic
  fun cleanProcessingFlag(file: VirtualFile) {
    setFileIndexed(file, ProjectIndexingDependenciesService.NULL_STAMP)
  }

  @JvmStatic
  fun cleanProcessingFlag(fileId: Int) {
    // file might have already been deleted, so there might be no VirtualFile for given fileId
    setFileIndexed(fileId, ProjectIndexingDependenciesService.NULL_STAMP)
  }

  @JvmStatic
  fun setFileIndexed(file: VirtualFile, stamp: FileIndexingStamp) {
    file.asApplicable()?.also { fileWithId ->
      setFileIndexed(fileWithId.id, stamp)
    }
  }

  private fun setFileIndexed(fileId: Int, stamp: FileIndexingStamp) {
    if (!isIndexedFlagDisabled()) {
      stamp.store { s ->
        persistence.writeLong(fileId, s)
      }
    }
  }

  @JvmStatic
  fun isFileIndexed(file: VirtualFile, stamp: FileIndexingStamp): Boolean {
    return file.asApplicable()?.let { fileWithId ->
      stamp.isSame(persistence.readLong(fileWithId.id))
    } ?: false
  }

  @JvmStatic
  fun isFileChanged(file: VirtualFile, stamp: FileIndexingStamp): IsFileChangedResult {
    return file.asApplicable()?.let { fileWithId ->
      stamp.isFileChanged(persistence.readLong(fileWithId.id).toFileModCount())
    } ?: IsFileChangedResult.UNKNOWN
  }

  @JvmStatic
  fun getOrCreateHash(file: VirtualFile): Long {
    return file.asApplicable()?.let { fileWithId -> hashes.getHash(fileWithId.id) } ?: nonExistentHash
  }

  @JvmStatic
  fun unlockFile(file: VirtualFile) {
    file.asApplicable()?.also { fileWithId -> hashes.releaseHash(fileWithId.id) }
  }

  @JvmStatic
  fun setIndexedIfFileWithSameLock(file: VirtualFile, lockObject: Long, stamp: FileIndexingStamp) {
    file.asApplicable()?.also { fileWithId ->
      val hash = hashes.releaseHash(fileWithId.id)
      if (!isFileIndexed(file, stamp)) {
        if (hash == lockObject) {
          setFileIndexed(file, stamp)
        }
        else {
          cleanProcessingFlag(file)
        }
      }
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