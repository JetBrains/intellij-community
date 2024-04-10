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
import kotlin.io.path.*


@ApiStatus.Internal
object PersistentDirtyFilesQueue {
  private val isUnittestMode: Boolean
    get() = ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode

  const val currentVersion = 2L

  const val queuesDirName: String = "dirty-file-queues"

  @JvmStatic
  fun getQueuesDir(): Path = PathManager.getIndexRoot() / queuesDirName

  @JvmStatic
  fun getQueueFile(): Path = PathManager.getIndexRoot() / "dirty-file-ids"

  @JvmStatic
  fun Project.getQueueFile(): Path = getQueuesDir() / locationHash

  @JvmStatic
  fun removeCurrentFile(queueFile: Path) {
    if (isUnittestMode) {
      thisLogger().info("removing ${queueFile.absolutePathString()}")
    }
    try {
      queueFile.deleteIfExists()
    }
    catch (ignored: IOException) {
    }
  }

  @JvmStatic
  fun readProjectDirtyFilesQueue(queueFile: Path, currentVfsVersion: Long?): ProjectDirtyFilesQueue {
    val (fileIds, index) = readIndexingQueue(queueFile, currentVfsVersion)
    return ProjectDirtyFilesQueue(fileIds, index ?: 0L)
  }

  @JvmStatic
  fun readOrphanDirtyFilesQueue(queueFile: Path, currentVfsVersion: Long?): OrphanDirtyFilesQueue {
    val (fileIds, index) = readIndexingQueue(queueFile, currentVfsVersion)
    return OrphanDirtyFilesQueue(fileIds, index ?: fileIds.size.toLong())
  }

  /**
   * Project dirty files queue and orphan dirty files queue have the same format
   * Project queue: [version, vfs version, last seen index in orphan queue, ids...]
   * Orphan queue: [version, vfs version, last index in queue, ids...]
   */
  @JvmStatic
  fun readIndexingQueue(queueFile: Path, currentVfsVersion: Long?): Pair<List<Int>, Long?> {
    try {
      return DataInputStream(queueFile.inputStream().buffered()).use {
        val result = IntArrayList()
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
        if (currentVfsVersion == null || storedVfsVersion == currentVfsVersion) {
          while (it.available() > 0) {
            result.add(it.readInt())
          }
        }
        else {
          thisLogger().info("Discarding dirty files queue $queueFile because vfs version changed: old=$storedVfsVersion, new=$currentVfsVersion")
        }
        if (isUnittestMode) {
          thisLogger().info("read dirty file ids: ${result.toIntArray().contentToString()}")
        }
        Pair(result, index)
      }
    }
    catch (ignored: NoSuchFileException) {
    }
    catch (ignored: EOFException) {
    }
    catch (e: IOException) {
      thisLogger().info(e)
    }
    return Pair(IntArrayList(), null)
  }

  @JvmStatic
  fun storeIndexingQueue(queueFile: Path, fileIds: List<Int>, index: Long, vfsVersion: Long) {
    storeIndexingQueue(queueFile, fileIds, index, vfsVersion, currentVersion)
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
      thisLogger().info("dirty file ids stored. Ids & filenames: ${idsToPaths.toString().take(300)}")
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
  fun store(vfsVersion: Long) {
    PersistentDirtyFilesQueue.storeIndexingQueue(getQueueFile(), fileIds, untrimmedSize, vfsVersion)
  }

  fun plus(ids: Collection<Int>): OrphanDirtyFilesQueue {
    val newIds = IntArrayList(fileIds)
    newIds.addAll(ids)
    return OrphanDirtyFilesQueue(newIds, untrimmedSize + ids.size)
  }
}
