// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.components.impl.ProjectPathMacroManager
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.project.stateStore
import com.intellij.util.PathUtil
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.io.systemIndependentPath
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.jps.serialization.*
import org.jdom.Element
import org.jetbrains.jps.util.JpsPathUtil
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Supplier

class ProjectStoreBridge(private val project: Project) : ModuleSavingCustomizer {
  override fun createSaveSessionProducerManager(): ProjectSaveSessionProducerManager {
    return ProjectWithModulesSaveSessionProducerManager(project)
  }

  override fun saveModules(projectSaveSessionManager: SaveSessionProducerManager, store: IProjectStore) {
    val writer = JpsStorageContentWriter(projectSaveSessionManager as ProjectWithModulesSaveSessionProducerManager, store, project)
    project.getComponent(JpsProjectModelSynchronizer::class.java).saveChangedProjectEntities(writer)
  }

  override fun commitModuleComponents(projectSaveSessionManager: SaveSessionProducerManager,
                                      moduleStore: ComponentStoreImpl,
                                      moduleSaveSessionManager: SaveSessionProducerManager) {
    (projectSaveSessionManager as ProjectWithModulesSaveSessionProducerManager).commitComponents(moduleStore, moduleSaveSessionManager)
  }
}

private class JpsStorageContentWriter(private val session: ProjectWithModulesSaveSessionProducerManager,
                                      private val store: IProjectStore,
                                      private val project: Project) : JpsFileContentWriter {
  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (FileUtil.extensionEquals(filePath, "iml")) {
      session.setModuleComponentState(filePath, componentName, componentTag)
    }
    else if (isExternalModuleFile(filePath)) {
      session.setExternalModuleComponentState(FileUtil.getNameWithoutExtension(PathUtil.getFileName(filePath)), componentName, componentTag)
    }
    else {
      val stateStorage = getProjectStateStorage(filePath, store, project) ?: return
      val producer = session.getProducer(stateStorage)
      if (producer is DirectoryBasedSaveSessionProducer) {
        producer.setFileState(PathUtil.getFileName(filePath), componentName, componentTag?.children?.first())
      }
      else {
        producer?.setState(null, componentName, componentTag)
      }
    }
  }

  override fun getReplacePathMacroMap(fileUrl: String): PathMacroMap {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    return if (FileUtil.extensionEquals(filePath, "iml") || isExternalModuleFile(filePath)) {
      ModulePathMacroManager.createInstance(project::getProjectFilePath, Supplier { filePath }).replacePathMap
    }
    else {
      ProjectPathMacroManager.getInstance(project).replacePathMap
    }
  }
}

private val MODULE_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(StoragePathMacros.MODULE_FILE, false)

private class ProjectWithModulesSaveSessionProducerManager(project: Project) : ProjectSaveSessionProducerManager(project) {
  companion object {
    private val NULL_ELEMENT = Element("null")
  }
  private val internalModuleComponents: ConcurrentMap<String, ConcurrentHashMap<String, Element>> = if (!SystemInfoRt.isFileSystemCaseSensitive)
    ConcurrentCollectionFactory.createConcurrentMap(HashingStrategy.caseInsensitive()) else ConcurrentCollectionFactory.createConcurrentMap()
  private val externalModuleComponents = ConcurrentHashMap<String, ConcurrentHashMap<String, Element>>()

  fun setModuleComponentState(imlFilePath: String, componentName: String, componentTag: Element?) {
    val componentToElement = internalModuleComponents.computeIfAbsent(imlFilePath) { ConcurrentHashMap() }
    componentToElement[componentName] = componentTag ?: NULL_ELEMENT
  }

  fun setExternalModuleComponentState(moduleFileName: String, componentName: String, componentTag: Element?) {
    val componentToElement = externalModuleComponents.computeIfAbsent(moduleFileName) { ConcurrentHashMap() }
    componentToElement[componentName] = componentTag ?: NULL_ELEMENT
  }

  fun commitComponents(moduleStore: ComponentStoreImpl, moduleSaveSessionManager: SaveSessionProducerManager) {
    fun commitToStorage(storageSpec: Storage, componentToElement: Map<String, Element>) {
      val storage = moduleStore.storageManager.getStateStorage(storageSpec)
      val producer = moduleSaveSessionManager.getProducer(storage)
      if (producer != null) {
        componentToElement.forEach { (componentName, componentTag) ->
          producer.setState(null, componentName, if (componentTag === NULL_ELEMENT) null else componentTag)
        }
      }
    }

    val moduleFilePath = moduleStore.storageManager.expandMacro(StoragePathMacros.MODULE_FILE)
    val internalComponents = internalModuleComponents[moduleFilePath.systemIndependentPath]
    if (internalComponents != null) {
      commitToStorage(MODULE_FILE_STORAGE_ANNOTATION, internalComponents)
    }

    val moduleFileName = FileUtil.getNameWithoutExtension(moduleFilePath.fileName.toString())
    val externalComponents = externalModuleComponents[moduleFileName]
    if (externalComponents != null) {
      val providerFactory = StreamProviderFactory.EP_NAME.getExtensions(project).firstOrNull()
      if (providerFactory != null) {
        val storageSpec = providerFactory.getOrCreateStorageSpec(StoragePathMacros.MODULE_FILE)
        commitToStorage(storageSpec, externalComponents)
      }
    }
  }
}

internal class StorageJpsConfigurationReader(private val project: Project,
                                             private val configLocation: JpsProjectConfigLocation) : JpsFileContentReaderWithCache {
  @Volatile
  private var fileContentCachingReader: CachingJpsFileContentReader? = null

  override fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String?): Element? {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (ProjectUtil.isRemotePath(FileUtil.toSystemDependentName(filePath)) && !project.isTrusted()) {
      throw IOException(ConfigurationStoreBundle.message("error.message.details.configuration.files.from.remote.locations.in.safe.mode"))
    }
    if (componentName == "") {
      //this is currently used for loading Eclipse project configuration from .classpath file
      val file = VirtualFileManager.getInstance().findFileByUrl(fileUrl)
      return file?.inputStream?.use { JDOMUtil.load(it) }
    }
    if (FileUtil.extensionEquals(filePath, "iml") || isExternalModuleFile(filePath)) {
      //todo fetch data from ModuleStore (https://jetbrains.team/p/wm/issues/51)
      return getCachingReader().loadComponent(fileUrl, componentName, customModuleFilePath)
    }
    else {
      val storage = getProjectStateStorage(filePath, project.stateStore, project) ?: return null
      val stateMap = storage.getStorageData()
      return if (storage is DirectoryBasedStorageBase) {
        val elementContent = stateMap.getElement(PathUtil.getFileName(filePath))
        if (elementContent != null) {
          Element(FileStorageCoreUtil.COMPONENT).setAttribute(FileStorageCoreUtil.NAME, componentName).addContent(elementContent)
        }
        else {
          null
        }
      }
      else {
        stateMap.getElement(componentName)
      }
    }
  }

  private fun getCachingReader(): CachingJpsFileContentReader {
    val reader = fileContentCachingReader ?: CachingJpsFileContentReader(configLocation)
    if (fileContentCachingReader == null) {
      fileContentCachingReader = reader
    }
    return reader
  }

  override fun getExpandMacroMap(fileUrl: String): ExpandMacroToPathMap {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (FileUtil.extensionEquals(filePath, "iml") || isExternalModuleFile(filePath)) {
      return getCachingReader().getExpandMacroMap(fileUrl)
    }
    else {
      return PathMacroManager.getInstance(project).expandMacroMap
    }
  }

  override fun clearCache() {
    fileContentCachingReader = null
  }
}

fun getProjectStateStorage(filePath: String,
                           store: IProjectStore,
                           project: Project): StateStorageBase<StateMap>? {
  val storageSpec = getStorageSpec(filePath, project) ?: return null
  @Suppress("UNCHECKED_CAST")
  return store.storageManager.getStateStorage(storageSpec) as StateStorageBase<StateMap>
}

private fun getStorageSpec(filePath: String, project: Project): Storage? {
  val collapsedPath: String
  val splitterClass: Class<out StateSplitterEx>
  val fileName = PathUtil.getFileName(filePath)
  val parentPath = PathUtil.getParentPath(filePath)
  val parentFileName = PathUtil.getFileName(parentPath)
  if (FileUtil.extensionEquals(filePath, "ipr") || fileName == "misc.xml" && parentFileName == ".idea") {
    collapsedPath = "\$PROJECT_FILE$"
    splitterClass = StateSplitterEx::class.java
  }
  else {
    if (parentFileName == Project.DIRECTORY_STORE_FOLDER) {
      collapsedPath = fileName
      splitterClass = StateSplitterEx::class.java
    }
    else {
      val grandParentPath = PathUtil.getParentPath(parentPath)
      collapsedPath = parentFileName
      splitterClass = FakeDirectoryBasedStateSplitter::class.java
      if (PathUtil.getFileName(grandParentPath) != Project.DIRECTORY_STORE_FOLDER) {
        val providerFactory = StreamProviderFactory.EP_NAME.getExtensions(project).firstOrNull() ?: return null
        if (parentFileName == "project") {
          if (fileName == "libraries.xml" || fileName == "artifacts.xml") {
            val inProjectStorage = FileStorageAnnotation(FileUtil.getNameWithoutExtension(fileName), false, splitterClass)
            val componentName = if (fileName == "libraries.xml") "libraryTable" else "ArtifactManager"
            return providerFactory.getOrCreateStorageSpec(fileName, StateAnnotation(componentName, inProjectStorage))
          }
          if (fileName == "modules.xml") {
            return providerFactory.getOrCreateStorageSpec(fileName)
          }
        }
        error("$filePath is not under .idea directory and not under external system cache")
      }
    }
  }
  return FileStorageAnnotation(collapsedPath, false, splitterClass)
}

/**
 * This fake implementation is used to force creating directory based storage in StateStorageManagerImpl.createStateStorage
 */
private class FakeDirectoryBasedStateSplitter : StateSplitterEx() {
  override fun splitState(state: Element): MutableList<Pair<Element, String>> {
    throw AssertionError()
  }
}
