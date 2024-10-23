// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.execution.impl

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.util.toBufferExposingByteArray
import org.jdom.Element
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

private val LOG: Logger
  get() = logger<RCInArbitraryFileManager>()

/**
 * Manages run configurations that are stored in arbitrary `*.run.xml` files in a project
 * (not in .idea/runConfigurations or project.ipr file).
 */
internal class RCInArbitraryFileManager(private val project: Project) {
  internal class DeletedAndAddedRunConfigs(deleted: Collection<RunnerAndConfigurationSettingsImpl>,
                                           added: Collection<RunnerAndConfigurationSettingsImpl>) {
    // new lists are created to make sure that lists used in a model are not available outside this class
    val deletedRunConfigs: Collection<RunnerAndConfigurationSettingsImpl> = if (deleted.isEmpty()) emptyList() else ArrayList(deleted)
    val addedRunConfigs: Collection<RunnerAndConfigurationSettingsImpl> = if (added.isEmpty()) emptyList() else ArrayList(added)
  }

  private val filePathToRunConfigs = mutableMapOf<String, MutableList<RunnerAndConfigurationSettingsImpl>>()

  // Remember digest in order not to overwrite file with an equivalent content (e.g. different line endings or smth non-meaningful)
  private val filePathToDigest = Collections.synchronizedMap(HashMap<String, LongArray>())

  @Volatile
  private var saveInProgress = false

  /**
   * this function should be called with RunManagerImpl.lock.write
   */
  internal fun addRunConfiguration(runConfig: RunnerAndConfigurationSettingsImpl) {
    val filePath = runConfig.pathIfStoredInArbitraryFileInProject
    if (!runConfig.isStoredInArbitraryFileInProject || filePath == null) {
      LOG.error("Unexpected run configuration, path: $filePath")
      return
    }

    val runConfigs = filePathToRunConfigs.get(filePath)
    if (runConfigs != null) {
      if (!runConfigs.contains(runConfig)) {
        runConfigs.add(runConfig)
      }
    }
    else {
      filePathToRunConfigs.put(filePath, mutableListOf(runConfig))
    }
  }

  /**
   * This function should be called with RunManagerImpl.lock.write
   */
  internal fun removeRunConfiguration(runConfig: RunnerAndConfigurationSettingsImpl,
                                      removeRunConfigOnlyIfFileNameChanged: Boolean = false,
                                      deleteContainingFile: Boolean = true) {
    val fileEntryIterator = filePathToRunConfigs.iterator()
    for (fileEntry in fileEntryIterator) {
      val filePath = fileEntry.key
      val runConfigIterator = fileEntry.value.iterator()
      for (rc in runConfigIterator) {
        if (rc == runConfig ||
            rc.isTemplate && runConfig.isTemplate && rc.type == runConfig.type) {
          if (filePath != runConfig.pathIfStoredInArbitraryFileInProject || !removeRunConfigOnlyIfFileNameChanged) {
            runConfigIterator.remove()
            if (fileEntry.value.isEmpty()) {
              fileEntryIterator.remove()
              filePathToDigest.remove(filePath)
              if (deleteContainingFile) {
                LocalFileSystem.getInstance().findFileByPath(filePath)?.let { deleteFile(it) }
              }
            }
          }
          return
        }
      }
    }
  }

  private fun deleteFile(file: VirtualFile) {
    invokeLater(ModalityState.nonModal()) {
      runWriteAction {
        file.delete(this@RCInArbitraryFileManager)
      }
    }
  }

  /**
   * This function doesn't change the model, caller should iterate through the returned list and remove/add run configurations as needed.
   * This function should be called with RunManagerImpl.lock.read
   */
  internal fun loadChangedRunConfigsFromFile(runManager: RunManagerImpl, filePath: String): DeletedAndAddedRunConfigs {
    if (saveInProgress) {
      return DeletedAndAddedRunConfigs(emptyList(), emptyList())
    }

    // shadow mutable map to ensure unchanged model
    val filePathToRunConfigs: Map<String, List<RunnerAndConfigurationSettingsImpl>> = filePathToRunConfigs

    val file = LocalFileSystem.getInstance().findFileByPath(filePath)
    if (file == null || !file.isValid) {
      LOG.warn("It's unexpected that the file doesn't exist at this point ($filePath)")
      val rcsToDelete = filePathToRunConfigs.get(filePath) ?: emptyList()
      return DeletedAndAddedRunConfigs(rcsToDelete, emptyList())
    }

    if (!ProjectFileIndex.getInstance(project).isInContent(file)) {
      val rcsToDelete = filePathToRunConfigs.get(filePath) ?: emptyList()
      if (rcsToDelete.isNotEmpty()) {
        LOG.warn("It's unexpected that the model contains run configurations for file, which is not within the project content ($filePath)")
      }
      return DeletedAndAddedRunConfigs(rcsToDelete, emptyList())
    }

    val previouslyLoadedRunConfigs = filePathToRunConfigs.get(filePath) ?: emptyList()

    val element = try {
      JDOMUtil.load(file.inputStream)
    }
    catch (e: Exception) {
      LOG.warn("Failed to parse file $filePath", e)
      return DeletedAndAddedRunConfigs(previouslyLoadedRunConfigs, emptyList())
    }

    if (element.name != "component" || element.getAttributeValue("name") != "ProjectRunConfigurationManager") {
      LOG.trace("Unexpected root element ${element.name} with name=${element.getAttributeValue("name")} in $filePath")
      return DeletedAndAddedRunConfigs(previouslyLoadedRunConfigs, emptyList())
    }

    val loadedRunConfigs = mutableListOf<RunnerAndConfigurationSettingsImpl>()
    val rootElementForLoadedDigest = createRootElement()
    for (configElement in element.getChildren("configuration")) {
      try {
        val runConfig = RunnerAndConfigurationSettingsImpl(runManager)
        runConfig.readExternal(configElement, true)
        runConfig.storeInArbitraryFileInProject(filePath)
        loadedRunConfigs.add(runConfig)
        rootElementForLoadedDigest.addContent(runConfig.writeScheme())
      }
      catch (e: Throwable /* classloading problems are expected too */) {
        if (e is ControlFlowException) throw e
        LOG.warn("Failed to read run configuration in $filePath", e)
      }
    }

    val loadedDigest = computeDigest(rootElementForLoadedDigest.toBufferExposingByteArray())

    val previouslyLoadedDigests = filePathToDigest.get(filePath)
    if (previouslyLoadedDigests != null && previouslyLoadedDigests.contentEquals(loadedDigest)) {
      return DeletedAndAddedRunConfigs(emptyList(), emptyList())
    }
    else {
      filePathToDigest.put(filePath, loadedDigest)
      return DeletedAndAddedRunConfigs(previouslyLoadedRunConfigs, loadedRunConfigs)
    }
  }

  /**
   * This function doesn't change the model, caller should iterate through the returned list and remove run configurations.
   * This function should be called with RunManagerImpl.lock.read
   */
  internal fun findRunConfigsThatAreNotWithinProjectContent(): List<RunnerAndConfigurationSettingsImpl> {
    // shadow mutable map to ensure unchanged model
    val filePathToRunConfigs: Map<String, List<RunnerAndConfigurationSettingsImpl>> = filePathToRunConfigs
    if (filePathToRunConfigs.isEmpty()) return emptyList()

    val fileIndex = ProjectFileIndex.getInstance(project)
    val deletedRunConfigs = mutableListOf<RunnerAndConfigurationSettingsImpl>()

    for (entry in filePathToRunConfigs) {
      val filePath = entry.key
      val runConfigs = entry.value
      val file = LocalFileSystem.getInstance().findFileByPath(filePath)
      if (file == null) {
        if (!saveInProgress) {
          deletedRunConfigs.addAll(runConfigs)
          LOG.warn("It's unexpected that the file doesn't exist at this point ($filePath)")
        }
      }
      else {
        if (!fileIndex.isInContent(file)) {
          deletedRunConfigs.addAll(runConfigs)
        }
      }
    }

    return deletedRunConfigs
  }

  /**
   * This function should be called with RunManagerImpl.lock.read
   */
  internal fun getRunConfigsFromFiles(filePaths: Collection<String>): Collection<RunnerAndConfigurationSettingsImpl> {
    val result = mutableListOf<RunnerAndConfigurationSettingsImpl>()
    for (filePath in filePaths) {
      filePathToRunConfigs.get(filePath)?.let(result::addAll)
    }
    return result
  }

  /**
   * This function should be called with RunManagerImpl.lock.read
   */
  internal fun hasRunConfigsFromFile(filePath: String): Boolean {
    return filePathToRunConfigs.containsKey(filePath)
  }

  /**
   * This function should be called with RunManagerImpl.lock.read
   */
  internal suspend fun saveRunConfigs(lock: ReentrantReadWriteLock) {
    var error: Throwable? = null

    val filePaths = lock.read { filePathToRunConfigs.keys.sorted() }
    for (filePath in filePaths) {
      val rootElement = lock.read {
        val rootElement = createRootElement()
        for (runConfig in (filePathToRunConfigs.get(filePath) ?: return@read null)) {
          rootElement.addContent(runConfig.writeScheme())
        }
        rootElement
      } ?: continue

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
      catch (e: Exception) {
        val wrappedException = RuntimeException("Cannot save run configuration in $filePath", e)
        if (error == null) {
          error = wrappedException
        }
        else {
          error.addSuppressed(wrappedException)
        }
      }
      finally {
        saveInProgress = false
      }
    }

    error?.let {
      throw it
    }
  }

  private suspend fun saveToFile(filePath: String, data: BufferExposingByteArrayOutputStream) {
    writeAction {
      var file = LocalFileSystem.getInstance().findFileByPath(filePath)
      if (file == null) {
        val parentPath = PathUtil.getParentPath(filePath)
        val dir = VfsUtil.createDirectoryIfMissing(parentPath)
        if (dir == null) {
          LOG.error("Failed to create directory $parentPath")
          return@writeAction
        }

        file = dir.createChildData(this@RCInArbitraryFileManager, PathUtil.getFileName(filePath))
      }

      file.getOutputStream(this@RCInArbitraryFileManager).use { data.writeTo(it) }
    }
  }

  /**
   *  This function should be called with RunManagerImpl.lock.write
   */
  internal fun clearAllAndReturnFilePaths(): Collection<String> {
    val filePaths = filePathToRunConfigs.keys.toList()
    filePathToRunConfigs.clear()
    filePathToDigest.clear()
    return filePaths
  }
}

private fun createRootElement() = Element("component").setAttribute("name", "ProjectRunConfigurationManager")

private fun computeDigest(data: BufferExposingByteArrayOutputStream): LongArray {
  // 128-bit
  return longArrayOf(Hashing.komihash5_0().hashBytesToLong(data.internalBuffer, 0, data.size()),
                     Hashing.komihash5_0(745726263).hashBytesToLong(data.internalBuffer, 0, data.size()))
}

