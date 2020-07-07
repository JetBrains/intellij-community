// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.configurationStore.digest
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.GuiUtils
import com.intellij.util.PathUtil
import com.intellij.util.SmartList
import com.intellij.util.throwIfNotEmpty
import com.intellij.util.toBufferExposingByteArray
import org.jdom.Element
import java.io.ByteArrayInputStream

/**
 * Manages run configurations that are stored in arbitrary `*.run.xml` files in project (not in .idea/runConfigurations or project.ipr file).
 */
internal class RCInArbitraryFileManager(private val project: Project) {
  private val LOG = logger<RCInArbitraryFileManager>()

  internal class DeletedAndAddedRunConfigs(val deletedRunConfigs: Collection<RunnerAndConfigurationSettingsImpl>,
                                           val addedRunConfigs: Collection<RunnerAndConfigurationSettingsImpl>)

  private var saving = false
  private val filePathToRunConfigs = mutableMapOf<String, MutableList<RunnerAndConfigurationSettingsImpl>>()

  // Remember digest in order not to overwrite file with an equivalent content (e.g. different line endings or smth non-meaningful)
  private val filePathToDigests = mutableMapOf<String, MutableList<ByteArray>>()

  internal fun addRunConfiguration(runConfig: RunnerAndConfigurationSettingsImpl) {
    val filePath = runConfig.pathIfStoredInArbitraryFileInProject
    if (!runConfig.isStoredInArbitraryFileInProject || filePath == null) {
      LOG.error("Unexpected run configuration, path: $filePath")
      return
    }

    val runConfigs = filePathToRunConfigs[filePath]
    if (runConfigs != null) {
      if (!runConfigs.contains(runConfig)) {
        runConfigs.add(runConfig)
      }
    }
    else {
      filePathToRunConfigs[filePath] = mutableListOf(runConfig)
    }
  }

  internal fun removeRunConfiguration(runConfig: RunnerAndConfigurationSettingsImpl,
                                      removeRunConfigOnlyIfFileNameChanged: Boolean = false,
                                      deleteContainingFile: Boolean = true) {
    val fileEntryIterator = filePathToRunConfigs.iterator()
    for (fileEntry in fileEntryIterator) {
      val filePath = fileEntry.key
      val runConfigIterator = fileEntry.value.iterator()
      for (rc in runConfigIterator) {
        if (rc == runConfig) {
          if (filePath != runConfig.pathIfStoredInArbitraryFileInProject || !removeRunConfigOnlyIfFileNameChanged) {
            runConfigIterator.remove()
            if (fileEntry.value.isEmpty()) {
              fileEntryIterator.remove()
              filePathToDigests.remove(filePath)
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
    GuiUtils.invokeLaterIfNeeded(Runnable { runWriteAction { file.delete(this@RCInArbitraryFileManager) } }, ModalityState.NON_MODAL)
  }

  /**
   * This function doesn't change the model, caller should iterate through the returned list and remove/add run configurations as needed
   */
  internal fun loadChangedRunConfigsFromFile(runManager: RunManagerImpl, filePath: String): DeletedAndAddedRunConfigs {
    if (saving) return DeletedAndAddedRunConfigs(emptyList(), emptyList())

    // shadow mutable map to ensure unchanged model
    val filePathToRunConfigs: Map<String, List<RunnerAndConfigurationSettingsImpl>> = filePathToRunConfigs

    val file = LocalFileSystem.getInstance().findFileByPath(filePath)
    if (file == null) {
      LOG.warn("It's unexpected that the file doesn't exist at this point ($filePath)")
      val rcsToDelete = filePathToRunConfigs[filePath] ?: emptyList()
      return DeletedAndAddedRunConfigs(rcsToDelete, emptyList())
    }

    if (!ProjectFileIndex.getInstance(project).isInContent(file)) {
      val rcsToDelete = filePathToRunConfigs[filePath] ?: emptyList()
      if (rcsToDelete.isNotEmpty()) {
        LOG.warn("It's unexpected that the model contains run configurations for file, which is not within the project content ($filePath)")
      }
      return DeletedAndAddedRunConfigs(rcsToDelete, emptyList())
    }

    val previouslyLoadedRunConfigs = filePathToRunConfigs[filePath] ?: emptyList()

    val bytes = try {
      VfsUtil.loadBytes(file)
    }
    catch (e: Exception) {
      LOG.warn("Failed to load file $filePath: $e")
      return DeletedAndAddedRunConfigs(previouslyLoadedRunConfigs, emptyList())
    }

    val element = try {
      JDOMUtil.load(CharsetToolkit.inputStreamSkippingBOM(ByteArrayInputStream(bytes)))
    }
    catch (e: Exception) {
      LOG.info("Failed to parse file $filePath: $e")
      return DeletedAndAddedRunConfigs(previouslyLoadedRunConfigs, emptyList())
    }

    if (element.name != "component" || element.getAttributeValue("name") != "ProjectRunConfigurationManager") {
      LOG.info("Unexpected root element ${element.name} with name=${element.getAttributeValue("name")} in $filePath")
      return DeletedAndAddedRunConfigs(previouslyLoadedRunConfigs, emptyList())
    }

    val loadedRunConfigs = mutableListOf<RunnerAndConfigurationSettingsImpl>()
    val loadedDigests = mutableListOf<ByteArray>()

    for (configElement in element.getChildren("configuration")) {
      try {
        val runConfig = RunnerAndConfigurationSettingsImpl(runManager)

        // TEMPLATE_FLAG_ATTRIBUTE attribute is never set programmatically in *.run.xml files but we want to be sure it's not added somehow externally.
        // This is to make sure that it is not parsed as a template RC in runConfig.readExternal()
        configElement.removeAttribute(TEMPLATE_FLAG_ATTRIBUTE)
        runConfig.readExternal(configElement, true)
        runConfig.storeInArbitraryFileInProject(filePath)
        loadedRunConfigs.add(runConfig)
        loadedDigests.add(runConfig.writeScheme().digest())
      }
      catch (e: Exception) {
        LOG.warn("Failed to read run configuration in $filePath", e)
      }
    }

    val previouslyLoadedDigests = filePathToDigests[filePath] ?: emptyList<ByteArray>()
    if (sameDigests(loadedDigests, previouslyLoadedDigests)) {
      return DeletedAndAddedRunConfigs(emptyList(), emptyList())
    }
    else {
      filePathToDigests[filePath] = loadedDigests
      return DeletedAndAddedRunConfigs(previouslyLoadedRunConfigs, loadedRunConfigs)
    }
  }

  /**
   * This function doesn't change the model, caller should iterate through the returned list and remove run configurations
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
        deletedRunConfigs.addAll(runConfigs)
        LOG.warn("It's unexpected that the file doesn't exist at this point ($filePath)")
      }
      else {
        if (!fileIndex.isInContent(file)) {
          deletedRunConfigs.addAll(runConfigs)
        }
      }
    }

    return deletedRunConfigs
  }

  internal fun getRunConfigsFromFiles(filePaths: Collection<String>): Collection<RunnerAndConfigurationSettingsImpl> {
    val result = mutableListOf<RunnerAndConfigurationSettingsImpl>()
    for (filePath in filePaths) {
      filePathToRunConfigs[filePath]?.let { result.addAll(it) }
    }
    return result
  }

  internal fun saveRunConfigs() {
    val errors = SmartList<Throwable>()
    for (entry in filePathToRunConfigs.entries) {
      val filePath = entry.key
      val runConfigs = entry.value

      saving = true
      try {
        val rootElement = Element("component").setAttribute("name", "ProjectRunConfigurationManager")
        val newDigests = mutableListOf<ByteArray>()
        for (runConfig in runConfigs) {
          val element = runConfig.writeScheme()
          rootElement.addContent(element)
          newDigests.add(element.digest())
        }

        val previouslyLoadedDigests = filePathToDigests[filePath] ?: emptyList<ByteArray>()
        if (!sameDigests(newDigests, previouslyLoadedDigests)) {
          saveToFile(filePath, rootElement.toBufferExposingByteArray())
          filePathToDigests[filePath] = newDigests
        }
      }
      catch (e: Exception) {
        errors.add(RuntimeException("Cannot save run configuration in $filePath", e))
      }
      finally {
        saving = false
      }
    }

    throwIfNotEmpty(errors)
  }

  private fun sameDigests(digests1: List<ByteArray>, digests2: List<ByteArray>): Boolean {
    if (digests1.size != digests2.size) return false

    val iterator2 = digests2.iterator()
    digests1.forEach { if (!it.contentEquals(iterator2.next())) return@sameDigests false }

    return true
  }

  private fun saveToFile(filePath: String, byteOut: BufferExposingByteArrayOutputStream) {
    runWriteAction {
      var file = LocalFileSystem.getInstance().findFileByPath(filePath)
      if (file == null) {
        val parentPath = PathUtil.getParentPath(filePath)
        val dir = VfsUtil.createDirectoryIfMissing(parentPath)
        if (dir == null) {
          LOG.error("Failed to create directory $parentPath")
          return@runWriteAction
        }

        file = dir.createChildData(this@RCInArbitraryFileManager, PathUtil.getFileName(filePath))
      }

      file.getOutputStream(this@RCInArbitraryFileManager).use { byteOut.writeTo(it) }
    }
  }
}
