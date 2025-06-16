// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.PersistentDirtyFilesQueue.getQueueFile
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.createDirectories
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jetbrains.annotations.ApiStatus
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream


@ApiStatus.Internal
object PersistentDirtyFilesQueue {
  private val isUnittestMode: Boolean
    get() = ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode

  private const val CURRENT_VERSION = 2L

  const val QUEUES_DIR_NAME: String = "dirty-file-queues"

  @JvmStatic
  fun getQueuesDir(): Path = PathManager.getIndexRoot() / QUEUES_DIR_NAME

  @JvmStatic
  fun getQueueFile(): Path = PathManager.getIndexRoot() / "dirty-file-ids"

  @JvmStatic
  fun Project.getQueueFile(): Path = getQueuesDir() / locationHash

  @JvmStatic
  fun readProjectDirtyFilesQueue(queueFile: Path, currentVfsVersion: Long?): ProjectDirtyFilesQueue {
    val (fileIds, index) = readIndexingQueue(queueFile, currentVfsVersion)
    return ProjectDirtyFilesQueue(fileIds, index ?: 0L)
  }

  @JvmStatic
  fun readOrphanDirtyFilesQueue(queueFile: Path, currentVfsVersion: Long?): Pair<OrphanDirtyFilesQueue, OrphanDirtyFilesQueueDiscardReason?> {
    val (fileIds, index, discardReason) = readIndexingQueue(queueFile, currentVfsVersion)
    return OrphanDirtyFilesQueue(fileIds, index ?: fileIds.size.toLong()) to discardReason
  }

  @ApiStatus.Internal
  data class IndexingQueueReadResult(val fileIds: List<Int>, val index: Long?, val orphanQueueDiscardReason: OrphanDirtyFilesQueueDiscardReason?)

  /**
   * Project dirty files queue and orphan dirty files queue have the same format
   * Project queue: [version, vfs version, last seen index in orphan queue, ids...]
   * Orphan queue: [version, vfs version, last index in queue, ids...]
   */
  @JvmStatic
  fun readIndexingQueue(queueFile: Path, currentVfsVersion: Long?): IndexingQueueReadResult {
    val error: Throwable? = try {
      DataInputStream(queueFile.inputStream().buffered()).use {
        val fileIds = IntArrayList()
        val version = it.readLong()
        val (storedVfsVersion, index) = when (version) {
          1L -> {
            val vfsVersion = it.readLong()
            Pair(vfsVersion, null)
          }
          2L -> {
            val vfsVersion = it.readLong()
            val index = it.readLong()
            Pair(vfsVersion, index)
          }
          else -> {
            // we can assume that small numbers are dirty files queue version and not vfs version
            // because vfs version is vfs creation timestamp that is System.currentTimeMillis()
            Pair(version, null)
          }
        }
        if (currentVfsVersion != null && storedVfsVersion != currentVfsVersion) {
          val message = "Discarding dirty files queue $queueFile because vfs version changed: old=$storedVfsVersion, new=$currentVfsVersion"
          thisLogger().info(message)
          return IndexingQueueReadResult(IntArrayList(), null, OrphanDirtyFilesQueueDiscardReason(message))
        }
        while (it.available() > 0) {
          fileIds.add(it.readInt())
        }
        thisLogger().info("Dirty file ids read. Size: ${fileIds.size}. Index: $index Path: $queueFile." +
                          if (isUnittestMode) " Ids: ${fileIds.toIntArray().contentToString()}" else "")
        return IndexingQueueReadResult(fileIds, index, null)
      }
    }
    catch (e: NoSuchFileException) {
      e
    }
    catch (e: EOFException) {
      e
    }
    catch (e: IOException) {
      thisLogger().info(e)
      e
    }
    val orphanQueueDiscardReason = error?.let { OrphanDirtyFilesQueueDiscardReason(error.toString()) }
    return IndexingQueueReadResult(IntArrayList(), null, orphanQueueDiscardReason)
  }

  @JvmStatic
  fun storeIndexingQueue(queueFile: Path, fileIds: List<Int>, index: Long, vfsVersion: Long) {
    storeIndexingQueue(queueFile, fileIds, index, vfsVersion, CURRENT_VERSION)
  }

  @JvmStatic
  fun storeIndexingQueue(queueFile: Path, fileIds: List<Int>, index: Long, vfsVersion: Long, version: Long) {
    try {
      if (fileIds.isEmpty()) {
        queueFile.deleteIfExists()
      }
      queueFile.parent.createDirectories()
      DataOutputStream(queueFile.outputStream().buffered()).use {
        if (version > 0) it.writeLong(version)
        it.writeLong(vfsVersion)
        it.writeLong(index)
        fileIds.forEach { fileId ->
          it.writeInt(fileId)
        }
      }
    }
    catch (e: IOException) {
      thisLogger().error(e)
    }
    if (isUnittestMode) {
      val idsToPaths = mapOf(*fileIds.map { it to StaleIndexesChecker.getStaleRecordOrExceptionMessage(it) }.toTypedArray())
      thisLogger().info("Dirty file ids stored. Size: ${fileIds.size}. Index: $index Path: $queueFile. Ids & filenames: ${idsToPaths.toString().take(300)}")
    }
    else {
      thisLogger().info("Dirty file ids stored. Size: ${fileIds.size}. Index: $index Path: $queueFile")
    }
  }
}

@ApiStatus.Internal
class ProjectDirtyFilesQueue(val fileIds: Collection<Int>, val lastSeenIndexInOrphanQueue: Long) {
  fun store(project: Project, vfsVersion: Long) {
    PersistentDirtyFilesQueue.storeIndexingQueue(project.getQueueFile(), IntArrayList(fileIds), lastSeenIndexInOrphanQueue, vfsVersion)
  }
}

@ApiStatus.Internal
class OrphanDirtyFilesQueue(val fileIds: List<Int>, val untrimmedSize: Long) {
  init {
    thisLogger().assertTrue(untrimmedSize >= fileIds.size, "untrimmedSize must be larger or equal to number of files in orphan queue. fileIds.size=${fileIds.size}, untrimmedSize=$untrimmedSize")
  }

  fun store(vfsVersion: Long) {
    PersistentDirtyFilesQueue.storeIndexingQueue(getQueueFile(), fileIds, untrimmedSize, vfsVersion)
  }

  fun plus(ids: Collection<Int>): OrphanDirtyFilesQueue {
    val newIds = IntArrayList(fileIds)
    newIds.addAll(ids)
    return OrphanDirtyFilesQueue(newIds, untrimmedSize + ids.size)
  }

  fun takeLast(maxSize: Int): OrphanDirtyFilesQueue {
    return if (maxSize <= 0) this
    else OrphanDirtyFilesQueue(fileIds.takeLast(maxSize), untrimmedSize)
  }
}

@ApiStatus.Internal
class OrphanDirtyFilesQueueDiscardReason(val message: String)
