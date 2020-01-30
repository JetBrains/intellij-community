// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.configurationStore.digest
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.toBufferExposingByteArray
import org.jdom.Element
import java.io.ByteArrayInputStream

/**
 * Manages run configurations that are stored in arbitrary files in project (not in .idea/runConfigurations).
 */
internal class RCInArbitraryFileManager(val project: Project) {
  private val LOG = logger<RCInArbitraryFileManager>()

  internal class DeletedAndAddedRunConfigs(val deletedRunConfigs: Collection<RunnerAndConfigurationSettingsImpl>,
                                           val addedRunConfigs: Collection<RunnerAndConfigurationSettingsImpl>)

  private class RunConfigInfo(val runConfig: RunnerAndConfigurationSettingsImpl, var digest: ByteArray)


  private val filePathToConfigurations = mutableMapOf<String, MutableList<RunConfigInfo>>()

  private fun addRunConfiguration(filePath: String, settings: RunnerAndConfigurationSettingsImpl, digest: ByteArray) {
    val runConfigs = filePathToConfigurations[filePath]
    if (runConfigs != null) {
      runConfigs.add(RunConfigInfo(settings, digest))
    }
    else {
      filePathToConfigurations[filePath] = mutableListOf(RunConfigInfo(settings, digest))
    }
  }

  internal fun reloadRunConfigsFromFile(runManager: RunManagerImpl, filePath: String): DeletedAndAddedRunConfigs {
    val file = LocalFileSystem.getInstance().findFileByPath(filePath)
    if (file == null) {
      LOG.warn("It's unexpected that the file doesn't exist at this point ($filePath)")
      val deletedRCs = filePathToConfigurations.remove(filePath)
      if (deletedRCs != null) {
        return DeletedAndAddedRunConfigs(deletedRCs.map { it.runConfig }, emptyList())
      }
      return DeletedAndAddedRunConfigs(emptyList(), emptyList())
    }
    else {
      return reloadRunConfigsFromFile(runManager, file)
    }
  }

  internal fun reloadRunConfigsFromFile(runManager: RunManagerImpl, file: VirtualFile): DeletedAndAddedRunConfigs {
    if (!ProjectFileIndex.getInstance(project).isInContent(file)) {
      return DeletedAndAddedRunConfigs(emptyList(), emptyList())
    }

    val path = file.path
    val previouslyLoadedRunConfigInfos = filePathToConfigurations[path] ?: emptyList<RunConfigInfo>()
    val previouslyLoadedRunConfigs = previouslyLoadedRunConfigInfos.map { it.runConfig }

    val bytes = try {
      VfsUtil.loadBytes(file)
    }
    catch (e: Exception) {
      LOG.warn("Failed to load file $path: $e")
      filePathToConfigurations.remove(path)
      return DeletedAndAddedRunConfigs(previouslyLoadedRunConfigs, emptyList())
    }

    val element = try {
      JDOMUtil.load(ByteArrayInputStream(bytes))
    }
    catch (e: Exception) {
      LOG.warn("Failed to parse file $path: $e")
      filePathToConfigurations.remove(path)
      return DeletedAndAddedRunConfigs(previouslyLoadedRunConfigs, emptyList())
    }

    if (element.name != "component" || element.getAttributeValue("name") != "ProjectRunConfigurationManager") {
      LOG.warn("Unexpected root element ${element.name} with name=${element.getAttributeValue("name")} in $path")
      filePathToConfigurations.remove(path)
      return DeletedAndAddedRunConfigs(previouslyLoadedRunConfigs, emptyList())
    }

    val loadedRunConfigInfos = mutableListOf<RunConfigInfo>()

    for (configElement in element.getChildren("configuration")) {
      try {
        val settings = RunnerAndConfigurationSettingsImpl(runManager)

        // TEMPLATE_FLAG_ATTRIBUTE attribute is never set programmatically in *.run.xml files but we want to be sure it's not added somehow externally.
        // This is to make sure that it is not parsed as a template RC in settings.readExternal()
        configElement.removeAttribute(TEMPLATE_FLAG_ATTRIBUTE)
        settings.readExternal(configElement, true)
        // readExternal() on the previous line sets level to PROJECT. But it must be ARBITRARY_FILE_IN_PROJECT
        settings.setStoreInArbitraryFileInProject()

        // Remember digest in order not to overwrite file with an equivalent content (e.g. different line endings or smth non-meaningful)
        // similar to com.intellij.execution.impl.RunConfigurationSchemeManager.readData
        val digest = settings.writeScheme().digest()
        loadedRunConfigInfos.add(RunConfigInfo(settings, digest))
      }
      catch (e: Exception) {
        LOG.warn("Failed to read run configuration in $path", e)
      }
    }

    if (sameRunConfigs(loadedRunConfigInfos, previouslyLoadedRunConfigInfos)) {
      return DeletedAndAddedRunConfigs(emptyList(), emptyList())
    }
    else {
      filePathToConfigurations[path] = loadedRunConfigInfos
      val loadedRunConfigs = loadedRunConfigInfos.map { it.runConfig }
      return DeletedAndAddedRunConfigs(previouslyLoadedRunConfigs, loadedRunConfigs)
    }
  }

  private fun sameRunConfigs(runConfigInfos1: List<RunConfigInfo>, runConfigInfos2: List<RunConfigInfo>): Boolean {
    if (runConfigInfos1.size != runConfigInfos2.size) return false

    val iterator = runConfigInfos2.iterator()
    runConfigInfos1.forEach { if (!it.digest.contentEquals(iterator.next().digest)) return@sameRunConfigs false }

    return true
  }

  internal fun saveRunConfigs() {
    val errors = SmartList<Throwable>()
    for (entry in filePathToConfigurations.entries) {
      val filePath = entry.key
      val rcInfos = entry.value

      try {
        var somethingChanged = false
        val rootElement = Element("component").setAttribute("name", "ProjectRunConfigurationManager")
        for (rcInfo in rcInfos) {
          val element = rcInfo.runConfig.writeScheme()
          val newDigest = element.digest()
          if (!rcInfo.digest.contentEquals(newDigest)) {
            rcInfo.digest = newDigest
            somethingChanged = true
          }
          rootElement.addContent(element)
        }

        if (somethingChanged) {
          saveToFile(filePath, rootElement.toBufferExposingByteArray())
        }
      }
      catch (e: Exception) {
        errors.add(RuntimeException("Cannot save run configuration in $filePath", e))
      }
    }

    CompoundRuntimeException.throwIfNotEmpty(errors)
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

        file = dir.createChildData(this, PathUtil.getFileName(filePath))
      }

      file.getOutputStream(this).use { byteOut.writeTo(it) }
    }
  }

  internal fun deleteRunConfigsFromArbitraryFilesNotWithinProjectContent(): List<RunnerAndConfigurationSettingsImpl> {
    if (filePathToConfigurations.isEmpty()) return emptyList()

    val fileIndex = ProjectFileIndex.getInstance(project)
    val deletedRunConfigs = mutableListOf<RunnerAndConfigurationSettingsImpl>()
    val iterator = filePathToConfigurations.iterator()

    for (entry in iterator) {
      val filePath = entry.key
      val rcInfos = entry.value
      val file = LocalFileSystem.getInstance().findFileByPath(filePath)
      if (file == null) {
        iterator.remove()
        rcInfos.forEach { deletedRunConfigs.add(it.runConfig) }
        LOG.warn("It's unexpected that the file doesn't exist at this point ($filePath)")
      }
      else {
        if (!fileIndex.isInContent(file)) {
          iterator.remove()
          rcInfos.forEach { deletedRunConfigs.add(it.runConfig) }
        }
      }
    }

    return deletedRunConfigs
  }

  internal fun deleteRunConfigsFromFiles(filePaths: Collection<String>): Collection<RunnerAndConfigurationSettingsImpl> {
    val deletedRunConfigs = mutableListOf<RunnerAndConfigurationSettingsImpl>()

    for (filePath in filePaths) {
      val deletedRunConfigInfos = filePathToConfigurations.remove(filePath)

      if (deletedRunConfigInfos != null) {
        deletedRunConfigs.addAll(deletedRunConfigInfos.map { it.runConfig })
      }
    }

    return deletedRunConfigs
  }
}
