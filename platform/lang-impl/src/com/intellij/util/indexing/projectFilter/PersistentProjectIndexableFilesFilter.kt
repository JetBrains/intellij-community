// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.UnindexedFilesUpdater
import com.intellij.util.io.*
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import java.io.DataInputStream
import java.io.IOException
import java.nio.file.Path
import kotlin.math.abs

private val log = logger<PersistentProjectIndexableFilesFilter>()
private const val maxBufferSize = 100

internal class PersistentProjectIndexableFilesFilter(private val projectFilterDumpPath: Path, private val project: Project): IdFilter() {
  private val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
  @Volatile
  private var memorySnapshot: ConcurrentBitSet? = null
  private val memoryBuffer: IntList = IntArrayList()

  override fun containsFileId(fileId: Int): Boolean {
    var snapshot = memorySnapshot
    if (snapshot == null) {
      if (skipFilterRestoration()) {
        return true
      }
      snapshot = restoreFilter()
      memorySnapshot = snapshot
    }
    return snapshot.get(fileId)
  }

  private fun skipFilterRestoration(): Boolean {
    return UnindexedFilesUpdater.isIndexUpdateInProgress(project)
  }

  private fun calculateIdsByProject(): ConcurrentBitSet {
    val result = ConcurrentBitSet.create()
    fileBasedIndex.iterateIndexableFiles(ContentIterator {
      if (it is VirtualFileWithId) {
        result.set(it.id)
      }
      return@ContentIterator true
    }, project, null)
    return result
  }

  fun ensureFileIdPresent(fileId: Int, add: () -> Boolean) {
    assert(fileId > 0)

    val snapshot = memorySnapshot

    if (snapshot != null && snapshot.get(fileId)) {
      return
    }

    if (add()) {
      snapshot?.set(fileId)
      appendToBuffer(fileId)
    }
  }

  fun removeFileId(fileId: Int) {
    assert(fileId > 0)

    val snapshot = memorySnapshot
    if (snapshot != null && !snapshot.clear(fileId)) {
      return
    }

    appendToBuffer(-fileId)
  }

  private fun appendToBuffer(fileId: Int) {
    val flushBuffer = synchronized(memoryBuffer) {
      memoryBuffer.add(fileId)
      memoryBuffer.size >= maxBufferSize
    }
    if (flushBuffer) {
      flushBuffer()
    }
  }

  @Synchronized
  private fun flushBuffer() {
    try {
      flushToPersistentFilter()
    }
    catch (e: Exception) {
      log.error(e)
      clearPersistentFilter()
    }
  }

  fun drop() {
    synchronized(memoryBuffer) {
      memoryBuffer.clear()
    }
    memorySnapshot = null
    clear()
  }

  @Synchronized
  fun clear() {
    clearPersistentFilter()
  }

  @Synchronized
  private fun restoreFilter() : ConcurrentBitSet {
    try {
      flushToPersistentFilter()
      val filter = loadPersistentFilter()
      if (filter != null) {
        return filter
      }
    }
    catch (e: IOException) {
      clearPersistentFilter()
      log.error(e)
    }
    val filter = calculateIdsByProject()
    try {
      savePersistentFilter(filter)
    }
    catch (e: IOException) {
      log.error(e)
    }
    return filter
  }

  private fun clearPersistentFilter() {
    FileUtil.delete(projectFilterDumpPath)
  }

  @Throws(IOException::class)
  private fun savePersistentFilter(filter: ConcurrentBitSet) {
    DataOutputStream(projectFilterDumpPath.outputStream().buffered()).use {
      for (fileId in 0..filter.size()) {
        if (filter.get(fileId)) {
          DataInputOutputUtil.writeINT(it, fileId)
        }
      }
    }
  }

  @Throws(IOException::class)
  private fun flushToPersistentFilter() {
    if (!projectFilterDumpPath.exists()) {
      synchronized(memoryBuffer) {
        memoryBuffer.clear()
      }
      return
    }

    val toWrite = synchronized(memoryBuffer) {
      val array = memoryBuffer.toIntArray()
      memoryBuffer.clear()
      array
    }

    DataOutputStream(projectFilterDumpPath.outputStream(append = true).buffered()).use {
      for (fileId in toWrite) {
        DataInputOutputUtil.writeINT(it, fileId)
      }
    }
  }

  @Throws(IOException::class)
  private fun loadPersistentFilter() : ConcurrentBitSet? {
    val inputStream = projectFilterDumpPath.inputStreamIfExists() ?: return null
    return DataInputStream(inputStream.buffered()).use {
      val fileIds = ConcurrentBitSet.create()
      while (it.available() > 0) {
        val fileId = DataInputOutputUtil.readINT(it)
        if (fileId > 0) {
          fileIds.set(fileId)
        }
        else {
          assert(fileId < 0)
          fileIds.clear(abs(fileId))
        }
      }
      return@use fileIds
    }
  }
}