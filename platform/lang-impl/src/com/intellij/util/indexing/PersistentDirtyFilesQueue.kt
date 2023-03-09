// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.application
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.createDirectories
import com.intellij.util.io.inputStream
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntCollection
import it.unimi.dsi.fastutil.ints.IntList
import org.jetbrains.annotations.TestOnly
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.outputStream


class PersistentDirtyFilesQueue @TestOnly constructor(private val dirtyFilesQueueFile: Path) {
  private val isUnittestMode: Boolean
    get() = application.isUnitTestMode

  @Suppress("TestOnlyProblems")
  constructor() : this(PathManager.getIndexRoot() / "dirty-file-ids")

  fun removeCurrentFile() {
    if (isUnittestMode) {
      thisLogger().info("removing ${dirtyFilesQueueFile.absolutePathString()}")
    }
    try {
      dirtyFilesQueueFile.deleteIfExists()
    }
    catch (ignored: IOException) {
    }
  }

  fun readIndexingQueue(currentVfsVersion: Long): IntList {
    val result = IntArrayList()
    try {
      DataInputStream(dirtyFilesQueueFile.inputStream().buffered()).use {
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

  fun storeIndexingQueue(fileIds: IntCollection, vfsVersion: Long) {
    try {
      if (fileIds.isEmpty()) {
        dirtyFilesQueueFile.deleteIfExists()
      }
      dirtyFilesQueueFile.parent.createDirectories()
      DataOutputStream(dirtyFilesQueueFile.outputStream().buffered()).use {
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