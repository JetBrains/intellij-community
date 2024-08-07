// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.ide.projectView.actions.MarkRootActionBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.CanonicalPathPrefixTreeFactory
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.MultiMap
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.workspaceModel.ide.impl.legacyBridge.RootConfigurationAccessorForWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future

@ApiStatus.Internal
@State(name = "sourceFolderManager", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class SourceFolderManagerImpl(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : SourceFolderManager, PersistentStateComponent<SourceFolderManagerState>, Disposable {
  private val moduleNamesToSourceFolderState: MultiMap<String, SourceFolderModelState> = MultiMap.create()
  private var isDisposed = false
  private val mutex = Any()
  private var sourceFolders = CanonicalPathPrefixTreeFactory.createMap<SourceFolderModel>()
  private var sourceFoldersByModule = HashMap<String, ModuleModel>()

  private val operationsStates = ConcurrentLinkedQueue<Future<*>>()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val refreshFilesDispatcher = Dispatchers.IO.limitedParallelism(3)

  override fun addSourceFolder(module: Module, url: String, type: JpsModuleSourceRootType<*>) {
    synchronized(mutex) {
      sourceFolders[url] = SourceFolderModel(module, url, type)
      addUrlToModuleModel(module, url)
    }
    coroutineScope.launch(refreshFilesDispatcher) {
      VirtualFileManager.getInstance().refreshAndFindFileByUrl(url)
    }
  }

  override fun setSourceFolderPackagePrefix(url: String, packagePrefix: String?) {
    synchronized(mutex) {
      val sourceFolder = sourceFolders[url] ?: return
      sourceFolder.packagePrefix = packagePrefix
    }
  }

  override fun setSourceFolderGenerated(url: String, generated: Boolean) {
    synchronized(mutex) {
      val sourceFolder = sourceFolders[url] ?: return
      sourceFolder.generated = generated
    }
  }

  override fun removeSourceFolders(module: Module) {
    synchronized(mutex) {
      val moduleModel = sourceFoldersByModule.remove(module.name) ?: return
      moduleModel.sourceFolders.forEach { sourceFolders.remove(it) }
    }
  }

  override fun dispose() {
    assert(!isDisposed) { "Source folder manager already disposed" }
    isDisposed = true
  }

  @TestOnly
  fun isDisposed() = isDisposed

  @TestOnly
  fun getSourceFolders(moduleName: String) = synchronized(mutex) {
    sourceFoldersByModule[moduleName]?.sourceFolders
  }

  private fun removeSourceFolder(url: String) {
    synchronized(mutex) {
      val sourceFolder = sourceFolders.remove(url) ?: return
      val module = sourceFolder.module
      val moduleModel = sourceFoldersByModule[module.name] ?: return
      val sourceFolders = moduleModel.sourceFolders
      sourceFolders.remove(url)
      if (sourceFolders.isEmpty()) {
        sourceFoldersByModule.remove(module.name)
      }
    }
  }

  @Suppress("unused") // todo fix warning
  private class ModuleListenerImpl : ModuleListener {
    override fun modulesAdded(project: Project, modules: List<Module>) {
      (SourceFolderManager.getInstance(project) as SourceFolderManagerImpl).modulesAdded(modules)
    }
  }

  internal fun filesCreated(fileCreateEvents: List<VFileCreateEvent>) {
    val sourceFoldersToChange = mutableMapOf<Module, ArrayList<Pair<VirtualFile, SourceFolderModel>>>()
    val virtualFileManager = VirtualFileManager.getInstance()

    for (event in fileCreateEvents) {
      val allDescendantValues = synchronized(mutex) {
        sourceFolders.getDescendantValues(VfsUtilCore.pathToUrl(event.path))
      }

      for (sourceFolder in allDescendantValues) {
        val sourceFolderFile = virtualFileManager.refreshAndFindFileByUrl(sourceFolder.url)
        if (sourceFolderFile != null && sourceFolderFile.isValid) {
          sourceFoldersToChange.computeIfAbsent(sourceFolder.module) { ArrayList() }.add(Pair(event.file!!, sourceFolder))
          removeSourceFolder(sourceFolder.url)
        }
      }
    }

    val job = coroutineScope.async {
      for ((key, values) in sourceFoldersToChange.keys.groupBy { it.project }) {
        withContext(Dispatchers.EDT) {
          batchUpdateModelsInEdt(key, values) { model ->
            modifyModel(sourceFoldersToChange, model)
          }
        }
      }
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      ThreadingAssertions.assertEventDispatchThread()
      val completableFuture = job.asCompletableFuture()
      operationsStates.add(completableFuture)
      job.invokeOnCompletion {
        operationsStates.remove(completableFuture)
      }
    }
  }

  private fun modulesAdded(modules: List<Module>) {
    synchronized(mutex) {
      for (module in modules) {
        moduleNamesToSourceFolderState.remove(module.name)!!.forEach {
          loadSourceFolderState(it, module)
        }
      }
    }
  }

  override fun rescanAndUpdateSourceFolders() {
    val sourceFoldersToChange = HashMap<Module, ArrayList<Pair<VirtualFile, SourceFolderModel>>>()
    val virtualFileManager = VirtualFileManager.getInstance()

    val values = synchronized(mutex) { sourceFolders.values }
    for (sourceFolder in values) {
      val sourceFolderFile = virtualFileManager.refreshAndFindFileByUrl(sourceFolder.url)
      if (sourceFolderFile != null && sourceFolderFile.isValid) {
        sourceFoldersToChange.computeIfAbsent(sourceFolder.module) { ArrayList() }.add(Pair(sourceFolderFile, sourceFolder))
        removeSourceFolder(sourceFolder.url)
      }
    }
    updateSourceFolders(sourceFoldersToChange)
  }

  private fun updateSourceFolders(sourceFoldersToChange: Map<Module, List<Pair<VirtualFile, SourceFolderModel>>>) {
    for ((key, values) in sourceFoldersToChange.keys.groupBy { it.project }) {
      ApplicationManager.getApplication().invokeAndWait {
        batchUpdateModelsInEdt(key, values) { model ->
          modifyModel(sourceFoldersToChange, model)
        }
      }
    }
  }

  private fun modifyModel(
    sourceFoldersToChange: Map<Module, List<Pair<VirtualFile, SourceFolderModel>>>,
    model: ModifiableRootModel,
  ) {
    val p = sourceFoldersToChange[model.module] ?: error("Value for the module ${model.module.name} should be available")
    for ((eventFile, sourceFolders) in p) {
      val (_, url, type, packagePrefix, generated) = sourceFolders
      val contentEntry = MarkRootActionBase.findContentEntry(model, eventFile) ?: model.addContentEntry(url, true)
      val sourceFolder = addSourceFolder(type, contentEntry, url, generated, packagePrefix)
      if (!packagePrefix.isNullOrEmpty()) {
        sourceFolder.packagePrefix = packagePrefix
      }
      setForGeneratedSources(sourceFolder, generated)
    }
  }

  private fun <P: JpsElement> addSourceFolder(
    type: JpsModuleSourceRootType<P>,
    contentEntry: ContentEntry,
    url: String,
    generated: Boolean,
    packagePrefix: String?,
  ): SourceFolder {
    val properties = type.createDefaultProperties()
    updateProperties(properties, generated, packagePrefix)
    return contentEntry.addSourceFolder(url, type, properties, true)
  }

  private fun updateProperties(properties: JpsElement, generated: Boolean, packagePrefix: String?) {
    when (properties) {
      is JavaSourceRootProperties -> {
        properties.isForGeneratedSources = generated
        if (!packagePrefix.isNullOrEmpty()) {
          properties.packagePrefix = packagePrefix
        }
      }
      is JavaResourceRootProperties ->
        properties.isForGeneratedSources = generated
      else -> { }
    }
  }

  @RequiresEdt
  private fun batchUpdateModelsInEdt(
    project: Project,
    modules: Collection<Module>,
    modifier: (ModifiableRootModel) -> Unit,
  ) {
    val diffBuilder = WorkspaceModel.getInstance(project).currentSnapshot.toBuilder()
    val modifiableRootModels = modules.asSequence().filter { !it.isDisposed }.map { module ->
      val moduleRootComponentBridge = ModuleRootManager.getInstance(module) as ModuleRootComponentBridge
      val modifiableRootModel = moduleRootComponentBridge.getModifiableModelForMultiCommit(ExternalSystemRootConfigurationAccessor(diffBuilder),
                                                                                           false)
      modifiableRootModel as ModifiableRootModelBridge
      modifier.invoke(modifiableRootModel)
      modifiableRootModel.prepareForCommit()
      modifiableRootModel
    }.toList()

    WriteAction.run<RuntimeException> {
      if (project.isDisposed) {
        return@run
      }

      WorkspaceModel.getInstance(project).updateProjectModel("Source folder manager: batch update models") { updater ->
        updater.applyChangesFrom(diffBuilder)
      }
      modifiableRootModels.forEach { it.postCommit() }
    }
  }

  private fun setForGeneratedSources(folder: SourceFolder, generated: Boolean) {
    val properties = folder.jpsElement.getProperties(JavaModuleSourceRootTypes.SOURCES)
    if (properties != null) {
      properties.isForGeneratedSources = generated
    }
  }

  override fun getState(): SourceFolderManagerState {
    synchronized(mutex) {
      return SourceFolderManagerState(
        sourceFolders.values.mapNotNull { model ->
          val modelTypeName = dictionary.entries.find { it.value == model.type }?.key ?: return@mapNotNull null
          SourceFolderModelState(
            moduleName = model.module.name,
            url = model.url,
            type = modelTypeName,
            packagePrefix = model.packagePrefix,
            generated = model.generated,
          )
        },
      )
    }
  }

  override fun loadState(state: SourceFolderManagerState) {
    synchronized(mutex) {
      resetModuleAddedListeners()
      if (isDisposed) {
        return
      }

      sourceFolders = CanonicalPathPrefixTreeFactory.createMap()
      sourceFoldersByModule = HashMap()

      if (state.sourceFolders.isEmpty()) {
        return
      }

      val moduleManager = ModuleManager.getInstance(project)

      for (model in state.sourceFolders) {
        val module = moduleManager.findModuleByName(model.moduleName)
        if (module == null) {
          listenToModuleAdded(model)
          continue
        }

        loadSourceFolderState(model, module)
      }
    }
  }

  private fun resetModuleAddedListeners() = moduleNamesToSourceFolderState.clear()
  private fun listenToModuleAdded(model: SourceFolderModelState) = moduleNamesToSourceFolderState.putValue(model.moduleName, model)

  private fun loadSourceFolderState(
    model: SourceFolderModelState,
    module: Module,
  ) {
    val rootType: JpsModuleSourceRootType<*> = dictionary[model.type] ?: return
    val url = model.url
    sourceFolders[url] = SourceFolderModel(
      module = module,
      url = url,
      type = rootType,
      packagePrefix = model.packagePrefix,
      generated = model.generated,
    )
    addUrlToModuleModel(module, url)
  }

  private fun addUrlToModuleModel(module: Module, url: String) {
    val moduleModel = sourceFoldersByModule.computeIfAbsent(module.name) {
      ModuleModel(module).also {
        Disposer.register(module, Disposable {
          removeSourceFolders(module)
        })
      }
    }
    moduleModel.sourceFolders.add(url)
  }

  @TestOnly
  @RequiresEdt
  fun consumeBulkOperationsState(stateConsumer: (Future<*>) -> Unit) {
    assert(ApplicationManager.getApplication().isUnitTestMode)
    do {
      val operation = operationsStates.poll() ?: break
      stateConsumer.invoke(operation)
    }
    while (true)
  }
}

private data class ModuleModel(
  @JvmField val module: Module,
  @JvmField val sourceFolders: MutableSet<String> = CollectionFactory.createFilePathSet(),
)

private data class SourceFolderModel(
  @JvmField val module: Module,
  @JvmField val url: String,
  @JvmField val type: JpsModuleSourceRootType<*>,
  @JvmField var packagePrefix: String? = null,
  @JvmField var generated: Boolean = false,
)

private val dictionary = mapOf<String, JpsModuleSourceRootType<*>>(
  "SOURCE" to JavaSourceRootType.SOURCE,
  "TEST_SOURCE" to JavaSourceRootType.TEST_SOURCE,
  "RESOURCE" to JavaResourceRootType.RESOURCE,
  "TEST_RESOURCE" to JavaResourceRootType.TEST_RESOURCE
)

@ApiStatus.Internal
class ExternalSystemRootConfigurationAccessor(
  override val actualDiffBuilder: MutableEntityStorage,
) : RootConfigurationAccessor(), RootConfigurationAccessorForWorkspaceModel

@ApiStatus.Internal
data class SourceFolderManagerState(
  @get:XCollection(style = XCollection.Style.v2) val sourceFolders: Collection<SourceFolderModelState>,
) {
  constructor() : this(mutableListOf())
}

@ApiStatus.Internal
data class SourceFolderModelState(
  var moduleName: String,
  var url: String,
  var type: String,
  var packagePrefix: String?,
  var generated: Boolean,
) {
  constructor() : this("", "", "", null, false)
}

private class SourceFolderManagerImplBulkFileListener : BulkFileListener {
  override fun after(events: List<VFileEvent>) {
    val fileCreateEvents = events.filterIsInstance<VFileCreateEvent>()
    if (fileCreateEvents.isEmpty()) return

    for (project in ProjectManager.getInstance().openProjects) {
      (SourceFolderManager.getInstance(project) as SourceFolderManagerImpl).filesCreated(fileCreateEvents)
    }
  }
}
