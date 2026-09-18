// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.execution.impl

import com.intellij.configurationStore.XmlProjectFileManager
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.StandardFileSystems
import org.jdom.Element
import java.util.concurrent.locks.ReentrantReadWriteLock

private val LOG: Logger
  get() = logger<RCInArbitraryFileManager>()

/**
 * Manages run configurations that are stored in arbitrary `*.run.xml` files in a project
 * (not in .idea/runConfigurations or project.ipr file).
 */
internal class RCInArbitraryFileManager(
  private val runManager: RunManagerImpl,
  lock: ReentrantReadWriteLock,
) : XmlProjectFileManager<MutableList<RunnerAndConfigurationSettingsImpl>>(runManager.project, lock, "run configurations") {
  internal class DeletedAndAddedRunConfigs(deleted: Collection<RunnerAndConfigurationSettingsImpl>,
                                           added: Collection<RunnerAndConfigurationSettingsImpl>) {
    // new lists are created to make sure that lists used in a model are not available outside this class
    val deletedRunConfigs: Collection<RunnerAndConfigurationSettingsImpl> = if (deleted.isEmpty()) emptyList() else ArrayList(deleted)
    val addedRunConfigs: Collection<RunnerAndConfigurationSettingsImpl> = if (added.isEmpty()) emptyList() else ArrayList(added)
  }

  /**
   * this function should be called with RunManagerImpl.lock.write
   */
  internal fun addRunConfiguration(runConfig: RunnerAndConfigurationSettingsImpl) {
    val filePath = runConfig.pathIfStoredInArbitraryFileInProject
    if (!runConfig.isStoredInArbitraryFileInProject || filePath == null) {
      LOG.error("Unexpected run configuration, path: $filePath")
      return
    }

    val runConfigs = filePathToData.get(filePath)
    if (runConfigs != null) {
      if (!runConfigs.contains(runConfig)) {
        runConfigs.add(runConfig)
      }
    }
    else {
      filePathToData.put(filePath, mutableListOf(runConfig))
    }
  }

  /**
   * This function should be called with RunManagerImpl.lock.write
   */
  internal fun removeRunConfiguration(runConfig: RunnerAndConfigurationSettingsImpl,
                                      removeRunConfigOnlyIfFileNameChanged: Boolean = false,
                                      deleteContainingFile: Boolean = true) {
    val fileEntryIterator = filePathToData.iterator()
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
                StandardFileSystems.local().findFileByPath(filePath)?.let { deleteFile(it) }
              }
            }
          }
          return
        }
      }
    }
  }

  internal fun loadChangedRunConfigsFromFile(filePath: String): DeletedAndAddedRunConfigs {
    val change = loadChangedFile(filePath)
    return DeletedAndAddedRunConfigs(change?.previous.orEmpty(), change?.current.orEmpty())
  }

  override fun readData(element: Element, filePath: String): LoadedData<MutableList<RunnerAndConfigurationSettingsImpl>>? {
    if (element.name != "component" || element.getAttributeValue("name") != "ProjectRunConfigurationManager") {
      return null
    }

    val loadedRunConfigs = mutableListOf<RunnerAndConfigurationSettingsImpl>()
    val rootElementForLoadedDigest = createRootElement()
    for (configElement in element.getChildren("configuration")) {
      try {
        val runConfig = RunnerAndConfigurationSettingsImpl(runManager)
        runConfig.readExternal(configElement, true, filePath)
        runConfig.storeInArbitraryFileInProject(filePath)
        loadedRunConfigs.add(runConfig)
        rootElementForLoadedDigest.addContent(runConfig.writeScheme())
      }
      catch (e: Throwable /* classloading problems are expected too */) {
        rethrowControlFlowException(e)
        LOG.warn("Failed to read run configuration in $filePath", e)
      }
    }
    return LoadedData(loadedRunConfigs, rootElementForLoadedDigest)
  }

  /**
   * This function should be called with RunManagerImpl.lock.read
   */
  internal fun getRunConfigsFromFiles(filePaths: Collection<String>): Collection<RunnerAndConfigurationSettingsImpl> {
    val result = mutableListOf<RunnerAndConfigurationSettingsImpl>()
    for (filePath in filePaths) {
      filePathToData.get(filePath)?.let(result::addAll)
    }
    return result
  }

  /**
   * This function should be called with RunManagerImpl.lock.read
   */
  internal fun hasRunConfigsFromFile(filePath: String): Boolean {
    return filePathToData.containsKey(filePath)
  }

  internal fun findRunConfigsThatAreNotWithinProjectContent(): List<RunnerAndConfigurationSettingsImpl> =
    findDataOutsideProjectContent().flatten()

  override fun writeData(data: MutableList<RunnerAndConfigurationSettingsImpl>): Element {
    val rootElement = createRootElement()
    for (runConfig in data) {
      rootElement.addContent(runConfig.writeScheme())
    }
    return rootElement
  }

  override fun snapshotData(data: MutableList<RunnerAndConfigurationSettingsImpl>): MutableList<RunnerAndConfigurationSettingsImpl> =
    data.toMutableList()

  override fun createSaveError(filePath: String, error: Exception): Throwable =
    RuntimeException("Cannot save run configuration in $filePath", error)
}

private fun createRootElement() = Element("component").setAttribute("name", "ProjectRunConfigurationManager")
