// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.application
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.createDirectories
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntCollection
import it.unimi.dsi.fastutil.ints.IntList
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.*


object PersistentDirtyFilesQueue {
  private val isUnittestMode: Boolean
    get() = application.isUnitTestMode

  @JvmStatic
  fun getQueueFile(): Path = PathManager.getIndexRoot() / "dirty-file-ids"

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
  fun readIndexingQueue(queueFile: Path, currentVfsVersion: Long): IntList {
    val result = IntArrayList()
    try {
      DataInputStream(queueFile.inputStream().buffered()).use {
        val storedVfsVersion = it.readLong()
        if (storedVfsVersion == currentVfsVersion) {
          while (it.available() > -1) {
            result.add(it.readInt())
          }
        }
      }
    }
    catch (ignored: NoSuchFileException) {
    }
    catch (ignored: EOFException) {
    }
    catch (e: IOException) {
      thisLogger().info(e)
    }
    if (isUnittestMode) {
      thisLogger().info("read dirty file ids: ${result.toIntArray().contentToString()}")
    }
    return result
  }

  @JvmStatic
  fun storeIndexingQueue(queueFile: Path, fileIds: IntCollection, vfsVersion: Long) {
    try {
      if (fileIds.isEmpty()) {
        queueFile.deleteIfExists()
      }
      queueFile.parent.createDirectories()
      DataOutputStream(queueFile.outputStream().buffered()).use {
        it.writeLong(vfsVersion)
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
