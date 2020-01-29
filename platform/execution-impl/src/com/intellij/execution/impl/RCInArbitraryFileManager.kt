// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.configurationStore.digest
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
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
internal class RCInArbitraryFileManager {
  private val LOG = logger<RCInArbitraryFileManager>()

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

  internal fun loadRunConfigsFromFile(runManager: RunManagerImpl, file: VirtualFile): List<RunnerAndConfigurationSettingsImpl> {
    val path = file.path

    val bytes = try {
      VfsUtil.loadBytes(file)
    }
    catch (e: Exception) {
      LOG.warn("Failed to load file $path: $e")
      return emptyList()
    }

    val element = try {
      JDOMUtil.load(ByteArrayInputStream(bytes))
    }
    catch (e: Exception) {
      LOG.warn("Failed to parse file $path: $e")
      return emptyList()
    }

    if (element.name != "component" || element.getAttributeValue("name") != "ProjectRunConfigurationManager") {
      LOG.warn("Unexpected root element ${element.name} with name=${element.getAttributeValue("name")} in $path")
      return emptyList()
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

    val knownRunConfigInfos = filePathToConfigurations[path]
    if (sameRunConfigs(loadedRunConfigInfos, knownRunConfigInfos)) {
      return emptyList()
    }
    else {
      filePathToConfigurations[path] = loadedRunConfigInfos
      return loadedRunConfigInfos.map { it.runConfig }
    }
  }

  private fun sameRunConfigs(runConfigs1: List<RunConfigInfo>, runConfigs2: List<RunConfigInfo>?): Boolean {
    if (runConfigs1.isEmpty()) return runConfigs2 == null || runConfigs2.isEmpty()
    if (runConfigs2 == null || runConfigs2.isEmpty()) return runConfigs1.isEmpty()
    if (runConfigs1.size != runConfigs2.size) return false

    val iterator = runConfigs2.iterator()
    runConfigs1.forEach { if (!it.digest.contentEquals(iterator.next().digest)) return@sameRunConfigs false }

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
}
