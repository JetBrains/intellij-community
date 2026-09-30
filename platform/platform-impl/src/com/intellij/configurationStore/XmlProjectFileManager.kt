// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.configurationStore

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.writeWithEnsureWritable
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.util.toBufferExposingByteArray
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.nio.file.AccessDeniedException
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

@ApiStatus.Internal
abstract class XmlProjectFileManager<T : Any>(
  protected val project: Project,
  protected val lock: ReentrantReadWriteLock,
  private val dataName: String,
) {
  protected val log: Logger
    get() = Logger.getInstance(javaClass)

  protected val filePathToData = mutableMapOf<String, T>()

  // Remember digest in order not to overwrite file with an equivalent content (e.g. different line endings or smth non-meaningful)
  protected val filePathToDigest = Collections.synchronizedMap(HashMap<String, LongArray>())

  @Volatile
  private var saveInProgress = false

  protected abstract fun readData(element: Element, filePath: String): LoadedData<T>?
  protected abstract fun writeData(data: T): Element
  protected open fun snapshotData(data: T): T = data
  protected abstract fun createSaveError(filePath: String, error: Exception): Throwable

  protected fun deleteFile(file: VirtualFile) {
    invokeLater(ModalityState.nonModal()) {
      runWriteAction {
        file.delete(this@XmlProjectFileManager)
      }
    }
  }

  /**
   * This function does not change the model. The caller must apply the returned [FileChange].
   * [lock] is taken only to snapshot the model.
   * [ProjectFileIndex.isInContent] may rebuild the workspace file index, whose contributors query the model under the same lock.
   * For run configurations, these contributors query RunManager.
   * Therefore, do not call this function while holding [lock].
   */
  protected fun loadChangedFile(filePath: String): FileChange<T>? {
    if (saveInProgress) {
      return null
    }

    fun dataFromFile() = lock.read { filePathToData.get(filePath)?.let(::snapshotData) }

    val file = StandardFileSystems.local().findFileByPath(filePath)
    if (file == null || !file.isValid) {
      log.warn("It's unexpected that the file doesn't exist at this point ($filePath)")
      return FileChange(dataFromFile(), null)
    }

    if (!ProjectFileIndex.getInstance(project).isInContent(file)) {
      val dataToDelete = dataFromFile()
      if (dataToDelete != null) {
        log.warn("It's unexpected that the model contains $dataName for file, which is not within the project content ($filePath)")
      }
      return FileChange(dataToDelete, null)
    }

    val previouslyLoadedData = dataFromFile()

    val element = try {
      JDOMUtil.load(file.inputStream)
    }
    catch (e: Exception) {
      log.warn("Failed to parse file $filePath", e)
      return FileChange(previouslyLoadedData, null)
    }

    val loadedData = readData(element, filePath)
    if (loadedData == null) {
      log.trace("Unexpected root element ${element.name} with name=${element.getAttributeValue("name")} in $filePath")
      return FileChange(previouslyLoadedData, null)
    }

    val loadedDigest = computeDigest(loadedData.element.toBufferExposingByteArray())

    val previouslyLoadedDigests = filePathToDigest.get(filePath)
    if (previouslyLoadedDigests != null && previouslyLoadedDigests.contentEquals(loadedDigest)) {
      return null
    }
    else {
      filePathToDigest.put(filePath, loadedDigest)
      return FileChange(previouslyLoadedData, loadedData.data)
    }
  }

  /**
   * This function does not change the model. The caller must remove the returned data from the model.
   * [lock] is taken only to snapshot the model.
   * [ProjectFileIndex.isInContent] may rebuild the workspace file index, whose contributors query the model under the same lock.
   * For run configurations, these contributors query RunManager.
   * Therefore, do not call this function while holding [lock].
   */
  protected fun findDataOutsideProjectContent(): List<T> {
    val filePathToData = lock.read { filePathToData.mapValues { snapshotData(it.value) } }
    if (filePathToData.isEmpty()) return emptyList()

    val fileIndex = ProjectFileIndex.getInstance(project)
    val deletedData = mutableListOf<T>()

    for (entry in filePathToData) {
      val filePath = entry.key
      val data = entry.value
      val file = StandardFileSystems.local().findFileByPath(filePath)
      if (file == null) {
        if (!saveInProgress) {
          deletedData.add(data)
          log.warn("It's unexpected that the file doesn't exist at this point ($filePath)")
        }
      }
      else {
        if (!fileIndex.isInContent(file)) {
          deletedData.add(data)
        }
      }
    }

    return deletedData
  }

  /**
   * This function takes [lock] in read mode to snapshot the file paths and serialize the model.
   * It releases the lock before file writes, which require a write action on the EDT.
   */
  suspend fun save() {
    var error: Throwable? = null

    val filePaths = lock.read { filePathToData.keys.sorted() }

    writeWithEnsureWritable(project, filePaths,
                            { filePath -> saveFile(filePath) },
                            { _, e ->
                                    if (error == null) {
                                      error = e
                                    }
                                    else {
                                      error.addSuppressed(e)
                                    }
                                  })

    error?.let {
      throw it
    }
  }

  private suspend fun saveFile(filePath: String) {
    val rootElement = lock.read {
      val data = filePathToData.get(filePath) ?: return@read null
      writeData(data)
    } ?: return

    saveInProgress = true
    try {
      val previouslyLoadedDigest = filePathToDigest.get(filePath)
      val data = rootElement.toBufferExposingByteArray()
      val newDigest = computeDigest(data)
      if (previouslyLoadedDigest == null || !newDigest.contentEquals(previouslyLoadedDigest)) {
        saveToFile(filePath = filePath, data = data)
        filePathToDigest.put(filePath, newDigest)
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: AccessDeniedException) {
      throw e
    }
    catch (e: Exception) {
      throw createSaveError(filePath, e)
    }
    finally {
      saveInProgress = false
    }
  }

  private suspend fun saveToFile(filePath: String, data: BufferExposingByteArrayOutputStream) {
    edtWriteAction {
      // the file is created and written in one write action; a cancellation of the saving coroutine between the two VFS operations
      // would leave an empty file behind (the VFS itself keeps a cancellation away from its listeners, see PersistentFSImpl.nonCancellableEventProcessing)
      Cancellation.withNonCancelableSection().use {
        var file = StandardFileSystems.local().findFileByPath(filePath)
        if (file == null) {
          val parentPath = PathUtil.getParentPath(filePath)
          val dir = VfsUtil.createDirectoryIfMissing(parentPath)
          if (dir == null) {
            log.error("Failed to create directory $parentPath")
            return@edtWriteAction
          }

          file = dir.createChildData(this@XmlProjectFileManager, PathUtil.getFileName(filePath))
        }

        file.getOutputStream(this@XmlProjectFileManager).use { data.writeTo(it) }
      }
    }
  }

  /**
   * This function must be called with [lock] in write mode.
   */
  fun clearAllAndReturnFilePaths(): Collection<String> {
    val filePaths = filePathToData.keys.toList()
    filePathToData.clear()
    filePathToDigest.clear()
    return filePaths
  }

  protected data class FileChange<T>(val previous: T?, val current: T?)
  protected data class LoadedData<T>(val data: T, val element: Element)
}

private fun computeDigest(data: BufferExposingByteArrayOutputStream): LongArray {
  // 128-bit
  return longArrayOf(Hashing.komihash5_0().hashBytesToLong(data.internalBuffer, 0, data.size()),
                     Hashing.komihash5_0(745726263).hashBytesToLong(data.internalBuffer, 0, data.size()))
}
