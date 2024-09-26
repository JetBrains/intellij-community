// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.isTrusted
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.components.impl.ProjectPathMacroManager
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getExternalConfigurationDir
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModelCache
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentWriter
import com.intellij.platform.workspace.jps.serialization.impl.isExternalModuleFile
import com.intellij.project.stateStore
import com.intellij.util.PathUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.containers.HashingStrategy
import com.intellij.workspaceModel.ide.getJpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.CachingJpsFileContentReader
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsFileContentReaderWithCache
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import com.intellij.workspaceModel.ide.impl.jps.serialization.ProjectStoreWithJpsContentReader
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import io.opentelemetry.api.metrics.Meter
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.util.JpsPathUtil
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Supplier
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
open class ProjectWithModuleStoreImpl(project: Project) : ProjectStoreImpl(project), ProjectStoreWithJpsContentReader {
  final override suspend fun saveModules(
    saveSessions: MutableList<SaveSession>,
    saveResult: SaveResult,
    forceSavingAllSettings: Boolean,
    projectSessionManager: ProjectSaveSessionProducerManager
  ) {
    projectSessionManager as ProjectWithModulesSaveSessionProducerManager

    val writer = JpsStorageContentWriter(session = projectSessionManager, store = this, project = project)
    project.serviceAsync<JpsProjectModelSynchronizer>().saveChangedProjectEntities(writer)
    (project.serviceAsync<WorkspaceModelCache>() as WorkspaceModelCacheImpl).doCacheSavingOnProjectClose()

    for (module in project.serviceAsync<ModuleManager>().modules) {
      val moduleStore = module.serviceAsync<IComponentStore>() as? ComponentStoreImpl ?: continue
      val moduleSessionManager = moduleStore.createSaveSessionProducerManager()
      moduleStore.commitComponents(isForce = forceSavingAllSettings, sessionManager = moduleSessionManager, saveResult = saveResult)
      projectSessionManager.commitComponents(moduleStore = moduleStore, moduleSaveSessionManager = moduleSessionManager)
      moduleSessionManager.collectSaveSessions(saveSessions)
    }
  }

  final override fun createSaveSessionProducerManager(): ProjectSaveSessionProducerManager {
    return ProjectWithModulesSaveSessionProducerManager(project, storageManager.isUseVfsForWrite)
  }

  override fun createContentReader(): JpsFileContentReaderWithCache {
    return StorageJpsConfigurationReader(project = project, configLocation = getJpsProjectConfigLocation(project)!!)
  }
}

private class JpsStorageContentWriter(
  private val session: ProjectWithModulesSaveSessionProducerManager,
  private val store: IProjectStore,
  private val project: Project,
) : JpsFileContentWriter {
  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (FileUtilRt.extensionEquals(filePath, "iml")) {
      saveInternalFileModuleComponent(filePath, componentName, componentTag)
    }
    else if (isExternalModuleFile(filePath)) {
      saveExternalFileModuleComponent(filePath, componentName, componentTag)
    }
    else {
      saveNonModuleComponent(filePath, componentName, componentTag)
    }
  }

  private fun saveInternalFileModuleComponent(filePath: @NlsSafe String, componentName: String, componentTag: Element?) {
    session.setModuleComponentState(imlFilePath = filePath, componentName = componentName, componentTag = componentTag)
  }

  private fun saveExternalFileModuleComponent(filePath: @NlsSafe String, componentName: String, componentTag: Element?) {
    session.setExternalModuleComponentState(
      moduleFileName = FileUtilRt.getNameWithoutExtension(PathUtilRt.getFileName(filePath)),
      componentName = componentName,
      componentTag = componentTag,
    )
  }

  private fun saveNonModuleComponent(filePath: @NlsSafe String, componentName: String, componentTag: Element?) {
    val stateStorage = getProjectStateStorage(filePath = filePath, store = store, project = project)
    val producer = session.getProducer(stateStorage)
    if (producer is DirectoryBasedSaveSessionProducer) {
      producer.setFileState(fileName = PathUtilRt.getFileName(filePath), componentName = componentName, element = componentTag?.children?.first())
    }
    else {
      producer?.setState(component = null, componentName = componentName, pluginId = PluginManagerCore.CORE_ID, state = componentTag)
    }
  }

  override fun getReplacePathMacroMap(fileUrl: String): PathMacroMap {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (FileUtilRt.extensionEquals(filePath, "iml") || isExternalModuleFile(filePath)) {
      return ModulePathMacroManager.createInstance(project::getProjectFilePath, Supplier { filePath }).replacePathMap
    }
    else {
      return ProjectPathMacroManager.getInstance(project).replacePathMap
    }
  }
}

private val MODULE_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(StoragePathMacros.MODULE_FILE, false)
private val NULL_ELEMENT = Element("null")

private class ProjectWithModulesSaveSessionProducerManager(project: Project, isUseVfsForWrite: Boolean)
  : ProjectSaveSessionProducerManager(project, isUseVfsForWrite) {
  private val internalModuleComponents: ConcurrentMap<String, ConcurrentHashMap<String, Element>> =
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      ConcurrentCollectionFactory.createConcurrentMap()
    }
    else {
      ConcurrentCollectionFactory.createConcurrentMap(HashingStrategy.caseInsensitive())
    }
  private val externalModuleComponents = ConcurrentHashMap<String, ConcurrentHashMap<String, Element>>()

  fun setModuleComponentState(imlFilePath: String, componentName: String, componentTag: Element?) {
    val componentToElement = internalModuleComponents.computeIfAbsent(imlFilePath) { ConcurrentHashMap() }
    componentToElement.put(componentName, componentTag ?: NULL_ELEMENT)
  }

  fun setExternalModuleComponentState(moduleFileName: String, componentName: String, componentTag: Element?) {
    val componentToElement = externalModuleComponents.computeIfAbsent(moduleFileName) { ConcurrentHashMap() }
    componentToElement.put(componentName, componentTag ?: NULL_ELEMENT)
  }

  fun commitComponents(moduleStore: ComponentStoreImpl, moduleSaveSessionManager: SaveSessionProducerManager) {
    fun commitToStorage(storageSpec: Storage, componentToElement: Map<String, Element>) {
      val storage = moduleStore.storageManager.getStateStorage(storageSpec)
      val producer = moduleSaveSessionManager.getProducer(storage)
      if (producer != null) {
        for ((componentName, componentTag) in componentToElement) {
          producer.setState(
            component = null,
            componentName = componentName,
            pluginId = PluginManagerCore.CORE_ID,
            state = if (componentTag === NULL_ELEMENT) null else componentTag,
          )
        }
      }
    }

    val moduleFilePath = moduleStore.storageManager.expandMacro(StoragePathMacros.MODULE_FILE)
    val internalComponents = internalModuleComponents.get(moduleFilePath.invariantSeparatorsPathString)
    if (internalComponents != null) {
      commitToStorage(MODULE_FILE_STORAGE_ANNOTATION, internalComponents)
    }

    val moduleFileName = FileUtilRt.getNameWithoutExtension(moduleFilePath.fileName.toString())
    val externalComponents = externalModuleComponents.get(moduleFileName)
    if (externalComponents != null) {
      StreamProviderFactory.EP_NAME.computeSafeIfAny(project) {
        it.getOrCreateStorageSpec(StoragePathMacros.MODULE_FILE)
      }?.let { commitToStorage(it, externalComponents) }
    }
  }
}

internal class StorageJpsConfigurationReader(private val project: Project, private val configLocation: JpsProjectConfigLocation) : JpsFileContentReaderWithCache {
  @Volatile
  private var fileContentCachingReader: CachingJpsFileContentReader? = null
  private val externalConfigurationDir = lazy { project.getExternalConfigurationDir() }

  override fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String?): Element? = loadComponentTimeMs.addMeasuredTime {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (ProjectUtil.isRemotePath(FileUtilRt.toSystemDependentName(filePath)) && !project.isTrusted()) {
      throw IOException(ConfigurationStoreBundle.message("error.message.details.configuration.files.from.remote.locations.in.safe.mode"))
    }
    if (componentName == "") {
      //this is currently used for loading Eclipse project configuration from .classpath file
      val file = VirtualFileManager.getInstance().findFileByUrl(fileUrl)
      val component = file?.inputStream?.use { JDOMUtil.load(it) }
      return@addMeasuredTime component
    }
    if (isExternalMiscFile(filePath)) {
      // this is a workaround to make a working scenario when the whole .idea is moved to external configuration dir
      // see com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectEntitiesLoader.isExternalStorageEnabled
      val component = getCachingReader().loadComponent(fileUrl, componentName, customModuleFilePath)
      return@addMeasuredTime component
    }
    if (FileUtilRt.extensionEquals(filePath, "iml") || isExternalModuleFile(filePath)) {
      //todo fetch data from ModuleStore (https://jetbrains.team/p/wm/issues/51)
      val component = getCachingReader().loadComponent(fileUrl, componentName, customModuleFilePath)
      return@addMeasuredTime component
    }
    else {
      val storage = getProjectStateStorage(filePath, project.stateStore, project)
      val stateMap = storage.getStorageData()
      val component = if (storage is DirectoryBasedStorage) {
        val elementContent = stateMap.getElement(PathUtilRt.getFileName(filePath))
        if (elementContent != null) {
          Element(ComponentStorageUtil.COMPONENT).setAttribute(ComponentStorageUtil.NAME, componentName).addContent(elementContent)
        }
        else {
          null
        }
      }
      else {
        stateMap.getElement(componentName)
      }

      return@addMeasuredTime component
    }
  }

  private fun isExternalMiscFile(filePath: String): Boolean {
    return PathUtilRt.getFileName(filePath) == "misc.xml" && Path.of(filePath).startsWith(externalConfigurationDir.value)
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

  companion object {
    private val loadComponentTimeMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val loadComponentTimeCounter = meter.counterBuilder("jps.storage.jps.conf.reader.load.component.ms").buildObserver()
      meter.batchCallback({ loadComponentTimeCounter.record(loadComponentTimeMs.asMilliseconds()) }, loadComponentTimeCounter)
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}

internal fun getProjectStateStorage(filePath: String, store: IProjectStore, project: Project): StateStorageBase<StateMap> {
  val storageSpec = getStorageSpec(filePath, project)
  @Suppress("UNCHECKED_CAST")
  return store.storageManager.getStateStorage(storageSpec) as StateStorageBase<StateMap>
}

private fun getStorageSpec(filePath: String, project: Project): Storage {
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
        if (parentFileName == "project") {
          if (fileName == "libraries.xml" || fileName == "artifacts.xml") {
            val inProjectStorage = FileStorageAnnotation(FileUtil.getNameWithoutExtension(fileName), false, splitterClass)
            val componentName = if (fileName == "libraries.xml") "libraryTable" else "ArtifactManager"
            StreamProviderFactory.EP_NAME.computeSafeIfAny(project) {
              it.getOrCreateStorageSpec(fileName, StateAnnotation(componentName, inProjectStorage))
            }?.let { return it }
          }
          if (fileName == "modules.xml") {
            StreamProviderFactory.EP_NAME.computeSafeIfAny(project) {
              it.getOrCreateStorageSpec(fileName)
            }?.let { return it }
          }
        }
        if (StreamProviderFactory.EP_NAME.hasAnyExtensions(project)) {
          error("$filePath is not under .idea directory and not under external system cache")
        }
      }
    }
  }
  return FileStorageAnnotation(/* path = */ collapsedPath, /* deprecated = */ false, /* splitterClass = */ splitterClass)
}

/**
 * This fake implementation is used to force creating directory-based storage in `StateStorageManagerImpl.createStateStorage`.
 */
private class FakeDirectoryBasedStateSplitter : StateSplitterEx() {
  override fun splitState(state: Element): MutableList<Pair<Element, String>> = throw AssertionError()
}
