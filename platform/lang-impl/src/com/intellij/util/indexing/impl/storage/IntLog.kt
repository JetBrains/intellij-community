// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.indexing.StorageException
import com.intellij.util.io.*
import com.intellij.util.io.keyStorage.AppendableObjectStorage
import com.intellij.util.io.keyStorage.AppendableStorageBackedByResizableMappedFile
import it.unimi.dsi.fastutil.ints.*
import org.jetbrains.annotations.VisibleForTesting
import java.io.*
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

interface AbstractIntLog : Closeable, Flushable {
  @FunctionalInterface
  interface IntLogEntryProcessor {
    fun process(data: Int, inputId: Int): Boolean
  }

  val modificationStamp: Long

  @Throws(StorageException::class)
  fun processEntries(processor: IntLogEntryProcessor): Boolean

  @Throws(StorageException::class)
  fun addData(data: Int, inputId: Int)

  @Throws(StorageException::class)
  fun removeData(data: Int, inputId: Int)

  fun clear()
}

class IntLog @Throws(IOException::class) constructor(private val baseStorageFile: Path,
                                                     compact: Boolean,
                                                     private val storageLockContext: StorageLockContext? = null) : AbstractIntLog {
  companion object {
    private val LOG = logger<IntLog>()
  }

  @Volatile
  private var myKeyHashToVirtualFileMapping: AppendableObjectStorage<IntArray>

  init {
    if (compact && isRequiresCompaction()) {
      performCompaction()
    }
    myKeyHashToVirtualFileMapping = openLog()
  }

  @Throws(StorageException::class)
  override fun processEntries(processor: AbstractIntLog.IntLogEntryProcessor): Boolean {
    try {
      val l = System.currentTimeMillis()
      doForce()
      val uniqueInputs = IntOpenHashSet()
      val uselessRecords = AtomicInteger()
      withLock(true) {
        ProgressManager.checkCanceled()
        if (!myKeyHashToVirtualFileMapping.processAll { key ->
            ProgressManager.checkCanceled()
            val inputId = key[1]
            val data = key[0]

            if (!processor.process(data, inputId)) {
              return@processAll false
            }

            if (!uniqueInputs.add(inputId)) {
              uselessRecords.incrementAndGet()
            }

            true
          }) {
          return@withLock false
        }
        true
      }
      if (uselessRecords.get() >= uniqueInputs.size) {
        setRequiresCompaction()
      }

      if (LOG.isDebugEnabled) {
        LOG.debug("Scanned IntLog of $baseStorageFile for ${System.currentTimeMillis() - l}")
      }

      return true
    }
    catch (e: IOException) {
      throw StorageException(e)
    }
  }

  @Throws(StorageException::class)
  override fun addData(data: Int, inputId: Int) {
    appendData(data, inputId)
  }

  @Throws(StorageException::class)
  override fun removeData(data: Int, inputId: Int) {
    appendData(data, -inputId)
  }

  override fun clear() {
    try {
      close()
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    IOUtil.deleteAllFilesStartingWith(baseStorageFile)
    myKeyHashToVirtualFileMapping = openLog()
  }

  private fun openLog() = AppendableStorageBackedByResizableMappedFile(getDataFile(),
                                                                       4096,
                                                                       storageLockContext,
                                                                       PagedFileStorage.MB,
                                                                       true,
                                                                       IntPairInArrayKeyDescriptor)

  @Throws(StorageException::class)
  private fun appendData(data: Int, inputId: Int) {
    if (inputId == 0) return
    try {
      withLock(false) { myKeyHashToVirtualFileMapping.append(intArrayOf(data, inputId)) }
    }
    catch (e: IOException) {
      throw StorageException(e)
    }
  }

  @Throws(IOException::class)
  override fun flush() {
    if (myKeyHashToVirtualFileMapping.isDirty) {
      doForce()
    }
  }

  @Throws(IOException::class)
  override fun close() {
    withLock(false) { myKeyHashToVirtualFileMapping.close() }
  }

  @Throws(IOException::class)
  private fun doForce() {
    withLock(false) { myKeyHashToVirtualFileMapping.force() }
  }

  override val modificationStamp = myKeyHashToVirtualFileMapping.currentLength.toLong()

  private fun <T> withLock(read: Boolean, operation: () -> T): T {
    if (read) {
      myKeyHashToVirtualFileMapping.lockRead()
    }
    else {
      myKeyHashToVirtualFileMapping.lockWrite()
    }
    try {
      return operation()
    }
    finally {
      if (read) {
        myKeyHashToVirtualFileMapping.unlockRead()
      }
      else {
        myKeyHashToVirtualFileMapping.unlockWrite()
      }
    }
  }

  private fun setRequiresCompaction() {
    val marker = getCompactionMarker()
    if (Files.exists(marker)) {
      return
    }
    try {
      Files.createFile(marker)
    }
    catch (ignored: FileAlreadyExistsException) {
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }

  @VisibleForTesting
  fun isRequiresCompaction(): Boolean {
    return Files.exists(getCompactionMarker())
  }

  private fun getCompactionMarker(): Path {
    val dataFile = getDataFile()
    return dataFile.resolveSibling(dataFile.fileName.toString() + ".require.compaction")
  }

  fun getDataFile(): Path {
    return baseStorageFile.resolveSibling(baseStorageFile.fileName.toString() + ".project")
  }

  @Throws(IOException::class)
  private fun performCompaction() {
    try {
      val data: Int2ObjectMap<IntSet> = Int2ObjectOpenHashMap()
      val oldDataFile = getDataFile()
      val oldMapping = AppendableStorageBackedByResizableMappedFile(oldDataFile,
                                                                    0,
                                                                    null,
                                                                    PagedFileStorage.MB,
                                                                    true,
                                                                    IntPairInArrayKeyDescriptor)
      oldMapping.lockRead()
      try {
        oldMapping.processAll { key: IntArray ->
          val inputId = key[1]
          val keyHash = key[0]
          val absInputId = abs(inputId)
          if (inputId > 0) {
            data.computeIfAbsent(keyHash, Int2ObjectFunction<IntSet> { IntOpenHashSet() }).add(absInputId)
          }
          else {
            val associatedInputIds = data[keyHash]
            associatedInputIds?.remove(absInputId)
          }
          true
        }
        oldMapping.close()
      }
      finally {
        oldMapping.unlockRead()
      }
      val dataFileName = oldDataFile.fileName.toString()
      val newDataFileName = "new.$dataFileName"
      val newDataFile = oldDataFile.resolveSibling(newDataFileName)
      val newMapping = AppendableStorageBackedByResizableMappedFile(newDataFile,
                                                                    32 * 2 * data.size,
                                                                    null,
                                                                    PagedFileStorage.MB,
                                                                    true,
                                                                    IntPairInArrayKeyDescriptor)
      newMapping.lockWrite()
      try {
        for (entry in data.int2ObjectEntrySet()) {
          val keyHash = entry.intKey
          val inputIdIterator = entry.value.iterator()
          while (inputIdIterator.hasNext()) {
            val inputId = inputIdIterator.nextInt()
            newMapping.append(intArrayOf(keyHash, inputId))
          }
        }
        newMapping.close()
      }
      finally {
        newMapping.unlockWrite()
      }
      IOUtil.deleteAllFilesStartingWith(oldDataFile.toFile())
      Files.newDirectoryStream(newDataFile.parent).use { paths ->
        for (path in paths) {
          val name = path.fileName.toString()
          if (name.startsWith(newDataFileName)) {
            FileUtil.rename(path.toFile(), dataFileName + name.substring(newDataFileName.length))
          }
        }
      }
      try {
        Files.delete(getCompactionMarker())
      }
      catch (ignored: IOException) {
      }
    }
    catch (e: ProcessCanceledException) {
      LOG.error(e)
      throw e
    }
  }

  private object IntPairInArrayKeyDescriptor : DataExternalizer<IntArray> {
    @Throws(IOException::class)
    override fun save(out: DataOutput, value: IntArray) {
      DataInputOutputUtil.writeINT(out, value[0])
      DataInputOutputUtil.writeINT(out, value[1])
    }

    @Throws(IOException::class)
    override fun read(`in`: DataInput): IntArray {
      return intArrayOf(DataInputOutputUtil.readINT(`in`), DataInputOutputUtil.readINT(`in`))
    }
  }

  override fun toString(): String {
    return "super.toString()"
  }
}