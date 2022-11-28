// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.application
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.createDirectories
import com.intellij.util.io.inputStream
import it.unimi.dsi.fastutil.ints.IntCollection
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.outputStream


internal object PersistentDirtyFilesQueue {
  private const val dirtyQueueFileName = "dirty-file-ids"

  private val isUnittestMode: Boolean
    get() = application.isUnitTestMode

  private val dirtyFilesQueueFile: Path
    get() = PathManager.getIndexRoot() / dirtyQueueFileName

  fun removeCurrentFile() {
    if (isUnittestMode) {
      thisLogger().info("removing $dirtyQueueFileName")
    }
    try {
      dirtyFilesQueueFile.deleteIfExists()
    }
    catch (ignored: IOException) {
    }
  }

  fun readIndexingQueue(): IntSet {
    val result = IntOpenHashSet()
    try {
      DataInputStream(dirtyFilesQueueFile.inputStream().buffered()).use {
        while (it.available() > -1) {
          result.add(it.readInt())
        }
      }
    }
    catch (ignored: NoSuchFileException) {
    }
    catch (ignored: EOFException) {
    }
    catch (e: IOException) {
      thisLogger().error(e)
    }
    if (isUnittestMode) {
      thisLogger().info("read dirty file ids: ${result.toIntArray().contentToString()}")
    }
    return result
  }

  fun storeIndexingQueue(fileIds: IntCollection) {
    try {
      dirtyFilesQueueFile.parent.createDirectories()
      DataOutputStream(dirtyFilesQueueFile.outputStream().buffered()).use {
        fileIds.forEach { fileId ->
          it.writeInt(fileId)
        }
      }
    }
    catch (e: IOException) {
      thisLogger().error(e)
    }
    if (isUnittestMode) {
      thisLogger().info("dirty file ids stored: ${fileIds.toIntArray().contentToString()}")
    }
  }
}