// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.ProjectTopics
import com.intellij.concurrency.JobSchedulerImpl
import com.intellij.configurationStore.saveComponentManager
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.impl.stores.ModuleStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.*
import com.intellij.openapi.module.impl.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.impl.ProjectServiceContainerInitializedListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.graph.*
import com.intellij.util.io.systemIndependentPath
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.executeOrQueueOnDispatchThread
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetEntityChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Callable

@Suppress("ComponentNotRegistered")
class ModuleManagerComponentBridge(private val project: Project) : ModuleManagerEx(), Disposable {
  private val LOG = Logger.getInstance(javaClass)

  internal val unloadedModules: MutableMap<String, UnloadedModuleDescriptionImpl> = LinkedHashMap()

  override fun dispose() {
    modules().forEach {
      Disposer.dispose(it)
    }
  }

  private fun modules(): Sequence<ModuleBridge> {
    return modules(entityStore.current)
  }

  internal class MyProjectServiceContainerInitializedListener : ProjectServiceContainerInitializedListener {
    override fun serviceCreated(project: Project) {
      val activity = StartUpMeasurer.startMainActivity("(wm) module loading")
      val manager = ModuleManager.getInstance(project) as? ModuleManagerComponentBridge ?: return

      val unloadedNames = UnloadedModulesListStorage.getInstance(project).unloadedModuleNames.toSet()
      val entities = manager.entityStore.current.entities(ModuleEntity::class.java)
        .filter { !unloadedNames.contains(it.name) }
        .toList()
      manager.loadModules(entities)
      activity.end()
      activity.setDescription("(wm) module count: ${manager.modules.size}")
      val librariesActivity = StartUpMeasurer.startMainActivity("(wm) project libraries loading")
      (LibraryTablesRegistrar.getInstance().getLibraryTable(project) as ProjectLibraryTableBridgeImpl).loadLibraries()
      librariesActivity.end()
      WorkspaceModelTopics.getInstance(project).notifyModulesAreLoaded()
    }
  }

  init {
    // default project doesn't have modules
    if (!project.isDefault) {
      val busConnection = project.messageBus.connect(this)
      busConnection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
        override fun projectOpened(eventProject: Project) {
          val activity = StartUpMeasurer.startActivity("(wm) ProjectManagerListener.projectOpened")
          if (project == eventProject) {
            fireModulesAdded()
            for (module in modules()) {
              module.projectOpened()
            }
          }
          activity.end()
        }

        override fun projectClosed(eventProject: Project) {
          if (project == eventProject) {
            for (module in modules()) {
              module.projectClosed()
            }
          }
        }
      })

      val rootsChangeListener = ProjectRootsChangeListener(project)
      WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, object : WorkspaceModelChangeListener {
        override fun beforeChanged(event: VersionedStorageChange) {
          for (change in event.getChanges(FacetEntity::class.java)) {
            LOG.debug { "Fire 'before' events for facet change $change" }
            FacetEntityChangeListener.getInstance(project).processBeforeChange(change)
          }
          val moduleMap = event.storageBefore.moduleMap
          for (change in event.getChanges(ModuleEntity::class.java)) {
            if (change is EntityChange.Removed) {
              val module = moduleMap.getDataByEntity(change.entity)
              LOG.debug { "Fire 'beforeModuleRemoved' event for module ${change.entity.name}, module = $module" }
              if (module != null) {
                fireBeforeModuleRemoved(module)
              }
            }
          }

          if (!VirtualFileUrlWatcher.getInstance(project).isInsideFilePointersUpdate) {
            //the old implementation doesn't fire rootsChanged event when roots are moved or renamed, let's keep this behavior for now
            rootsChangeListener.beforeChanged(event)
          }
        }

        override fun changed(event: VersionedStorageChange) {
          val moduleLibraryChanges = event.getChanges(LibraryEntity::class.java).filterModuleLibraryChanges()
          val changes = event.getChanges(ModuleEntity::class.java)
          val facetChanges = event.getChanges(FacetEntity::class.java)
          val addedModulesNames = changes.filterIsInstance<EntityChange.Added<ModuleEntity>>().mapTo(HashSet()) { it.entity.name }
          if (changes.isNotEmpty() || moduleLibraryChanges.isNotEmpty() || facetChanges.isNotEmpty()) {
            executeOrQueueOnDispatchThread {
              LOG.debug("Process changed modules and facets")
              incModificationCount()

              val unloadedModulesSetOriginal = unloadedModules.keys.toList()
              val unloadedModulesSet = unloadedModulesSetOriginal.toMutableSet()
              val oldModuleNames = mutableMapOf<Module, String>()

              for (change in moduleLibraryChanges) when (change) {
                is EntityChange.Removed -> processModuleLibraryChange(change, event)
                is EntityChange.Replaced -> processModuleLibraryChange(change, event)
                is EntityChange.Added -> Unit
              }

              for (change in facetChanges) when (change) {
                is EntityChange.Removed -> FacetEntityChangeListener.getInstance(project).processChange(change, event.storageBefore, addedModulesNames)
                is EntityChange.Replaced -> FacetEntityChangeListener.getInstance(project).processChange(change, event.storageBefore, addedModulesNames)
                is EntityChange.Added -> Unit
              }

              for (change in changes) processModuleChange(change, unloadedModulesSet, oldModuleNames, event)

              for (change in moduleLibraryChanges) when (change) {
                is EntityChange.Removed -> Unit
                is EntityChange.Replaced -> Unit
                is EntityChange.Added -> processModuleLibraryChange(change, event)
              }

              for (change in facetChanges) when (change) {
                is EntityChange.Removed -> Unit
                is EntityChange.Replaced -> Unit
                is EntityChange.Added -> FacetEntityChangeListener.getInstance(project).processChange(change, event.storageBefore, addedModulesNames)
              }

              // After every change processed
              postProcessModules(oldModuleNames, unloadedModulesSet)

              incModificationCount()
            }
          }

          // Roots changed should be sent after syncing with legacy bridge
          if (!VirtualFileUrlWatcher.getInstance(project).isInsideFilePointersUpdate) {
            //the old implementation doesn't fire rootsChanged event when roots are moved or renamed, let's keep this behavior for now
            rootsChangeListener.changed(event)
          }
        }
      })
    }
  }

  override fun areModulesLoaded(): Boolean {
    return WorkspaceModelTopics.getInstance(project).modulesAreLoaded
  }

  private fun postProcessModules(oldModuleNames: MutableMap<Module, String>,
                                 unloadedModulesSet: MutableSet<String>) {
    if (oldModuleNames.isNotEmpty()) {
      project.messageBus
        .syncPublisher(ProjectTopics.MODULES)
        .modulesRenamed(project, oldModuleNames.keys.toList()) { module -> oldModuleNames[module] }
    }

    if (unloadedModulesSet.isNotEmpty()) {
      val loadedModules = modules.map { it.name }.toMutableList()
      loadedModules.removeAll(unloadedModulesSet)
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(loadedModules)
    }
  }

  private fun processModuleChange(change: EntityChange<ModuleEntity>,
                                  unloadedModulesSet: MutableSet<String>,
                                  oldModuleNames: MutableMap<Module, String>,
                                  event: VersionedStorageChange) {
    when (change) {
      is EntityChange.Removed -> {
        // It's possible case then idToModule doesn't contain element e.g if unloaded module was removed
        val module = event.storageBefore.findModuleByEntity(change.entity)
        if (module != null) {
          fireEventAndDisposeModule(module)
        }
        unloadedModulesSet.remove(change.entity.name)
      }

      is EntityChange.Added -> {
        val alreadyCreatedModule = event.storageAfter.findModuleByEntity(change.entity)
        val module = if (alreadyCreatedModule != null) {
          unloadedModulesSet.remove(change.entity.name)
          unloadedModules.remove(change.entity.name)

          (alreadyCreatedModule as ModuleBridgeImpl).entityStorage = entityStore
          alreadyCreatedModule.diff = null
          alreadyCreatedModule
        }
        else {
          if (change.entity.name in unloadedModules.keys) {
            // Skip unloaded modules if it was not added via API
            return
          }

          if (!areModulesLoaded()) return

          addModule(change.entity)
        }

        if (project.isOpen) {
          fireModuleAddedInWriteAction(module)
        }
      }

      is EntityChange.Replaced -> {
        val oldId = change.oldEntity.persistentId()
        val newId = change.newEntity.persistentId()

        if (oldId != newId) {
          unloadedModulesSet.remove(change.newEntity.name)
          unloadedModules.remove(change.newEntity.name)
          val module = event.storageBefore.findModuleByEntity(change.oldEntity)
          if (module != null) {
            module as ModuleBridgeImpl
            module.rename(newId.name, getModuleVirtualFileUrl(change.newEntity), true)
            oldModuleNames[module] = oldId.name
          }
        }
      }
    }
  }

  private fun processModuleLibraryChange(change: EntityChange<LibraryEntity>, event: VersionedStorageChange) {
    when (change) {
      is EntityChange.Removed -> {
        val library = event.storageBefore.libraryMap.getDataByEntity(change.entity)
        if (library != null) {
          Disposer.dispose(library)
        }
      }
      is EntityChange.Replaced -> {
        val idBefore = change.oldEntity.persistentId()
        val idAfter = change.newEntity.persistentId()

        val newLibrary = event.storageAfter.libraryMap.getDataByEntity(change.newEntity) as LibraryBridgeImpl?
        if (newLibrary != null) {
          newLibrary.clearTargetBuilder()
          if (idBefore != idAfter) {
            newLibrary.entityId = idAfter
          }
        }
      }
      is EntityChange.Added -> {
        val tableId = change.entity.tableId as LibraryTableId.ModuleLibraryTableId
        val moduleEntity = entityStore.current.resolve(tableId.moduleId)
                           ?: error("Could not find module for module library: ${change.entity.persistentId()}")
        if (moduleEntity.name !in unloadedModules) {

          val library = event.storageAfter.libraryMap.getDataByEntity(change.entity)
          if (library == null && areModulesLoaded()) {
            val module = entityStore.current.findModuleByEntity(moduleEntity)
                         ?: error("Could not find module bridge for module entity $moduleEntity")
            val moduleRootComponent = ModuleRootComponentBridge.getInstance(module)
            moduleRootComponent.moduleLibraryTable.addLibrary(change.entity, null)
          }
          if (library != null) {
            (library as LibraryBridgeImpl).entityStorage = entityStore
            library.clearTargetBuilder()
          }
        }
      }
    }
  }

  private fun addModule(moduleEntity: ModuleEntity): ModuleBridge {
    val module = createModuleInstance(moduleEntity, entityStore, diff = null, isNew = true)
    WorkspaceModel.getInstance(project).updateProjectModelSilent {
      it.mutableModuleMap.addMapping(moduleEntity, module)
    }
    return module
  }

  private fun fireEventAndDisposeModule(module: ModuleBridge) {
    project.messageBus.syncPublisher(ProjectTopics.MODULES).moduleRemoved(project, module)
    Disposer.dispose(module)
  }

  private fun fireBeforeModuleRemoved(module: ModuleBridge) {
    project.messageBus.syncPublisher(ProjectTopics.MODULES).beforeModuleRemoved(project, module)
  }

  override fun moduleDependencyComparator(): Comparator<Module> {
    return entityStore.cachedValue(dependencyComparatorValue)
  }

  override fun moduleGraph(): Graph<Module> = entityStore.cachedValue(dependencyGraphWithTestsValue)

  override fun moduleGraph(includeTests: Boolean): Graph<Module> {
    return entityStore.cachedValue(if (includeTests) dependencyGraphWithTestsValue else dependencyGraphWithoutTestsValue)
  }

  private val entityStore by lazy { WorkspaceModel.getInstance(project).entityStorage }

  private fun loadModules(entities: List<ModuleEntity>) {
    LOG.debug { "Loading modules for ${entities.size} entities" }
    val fileSystem = LocalFileSystem.getInstance()
    entities.forEach { module -> getModuleVirtualFileUrl(module)?.let { fileSystem.refreshAndFindFileByNioFile(it.toPath()) } }

    val service = AppExecutorUtil.createBoundedApplicationPoolExecutor("ModuleManager Loader", JobSchedulerImpl.getCPUCoresCount())
    try {
      val tasks = entities
        .map { moduleEntity ->
          Callable {
            LOG.runAndLogException {
              val module = createModuleInstance(moduleEntity, entityStore, null, false)
              moduleEntity to module
            }
          }
        }

      val results = service.invokeAll(tasks)

      WorkspaceModel.getInstance(project).updateProjectModelSilent { builder ->
        val moduleMap = builder.mutableModuleMap
        results.mapNotNull { it.get() }.forEach { (entity, module) ->
          moduleMap.addMapping(entity, module)
          ModuleRootComponentBridge.getInstance(module).moduleLibraryTable.registerModuleLibraryInstances(builder)
        }
      }
    }
    finally {
      service.shutdownNow()
    }

    WriteAction.runAndWait<RuntimeException> {
      (ProjectRootManager.getInstance(project) as ProjectRootManagerBridge).setupTrackedLibrariesAndJdks()
    }
  }

  private fun fireModulesAdded() {
    for (module in modules()) {
      fireModuleAddedInWriteAction(module)
    }
  }

  private fun fireModuleAddedInWriteAction(module: ModuleEx) {
    ApplicationManager.getApplication().runWriteAction {
      if (!module.isLoaded) {
        module.moduleAdded()
        fireModuleAdded(module)
      }
    }
  }

  private fun fireModuleAdded(module: Module) {
    project.messageBus.syncPublisher(ProjectTopics.MODULES).moduleAdded(project, module)
  }

  override fun unloadNewlyAddedModulesIfPossible(modulesToLoad: MutableSet<ModulePath>,
                                                 modulesToUnload: MutableList<UnloadedModuleDescriptionImpl>) {
    val change = AutomaticModuleUnloader.getInstance(project).processNewModules(modulesToLoad, modulesToUnload)
    modulesToUnload.addAll(change.toUnloadDescriptions)
    unloadedModules.clear()
    modulesToUnload.associateByTo(unloadedModules) { it.name }
  }

  override fun getModifiableModel(): ModifiableModuleModel =
    ModifiableModuleModelBridgeImpl(project, this, WorkspaceEntityStorageBuilder.from(entityStore.current))

  fun getModifiableModel(diff: WorkspaceEntityStorageBuilder): ModifiableModuleModel =
    ModifiableModuleModelBridgeImpl(project, this, diff, false)

  override fun newModule(filePath: String, moduleTypeId: String): Module {
    incModificationCount()
    val modifiableModel = modifiableModel
    val module = modifiableModel.newModule(filePath, moduleTypeId)
    modifiableModel.commit()
    return module
  }

  override fun newNonPersistentModule(moduleName: String, id: String): Module {
    incModificationCount()
    val modifiableModel = modifiableModel
    val module = modifiableModel.newNonPersistentModule(moduleName, id)
    modifiableModel.commit()
    return module
  }

  override fun getModuleDependentModules(module: Module): List<Module> = modules.filter { isModuleDependent(it, module) }

  override fun getUnloadedModuleDescriptions(): Collection<UnloadedModuleDescription> = unloadedModules.values

  override fun getFailedModulePaths(): Collection<ModulePath> = emptyList()

  override fun hasModuleGroups(): Boolean = hasModuleGroups(entityStore)

  override fun isModuleDependent(module: Module, onModule: Module): Boolean =
    ModuleRootManager.getInstance(module).isDependsOn(onModule)

  override fun getAllModuleDescriptions(): Collection<ModuleDescription> =
    (modules().map { LoadedModuleDescriptionImpl(it) } + unloadedModuleDescriptions).toList()

  override fun getModuleGroupPath(module: Module): Array<String>? = getModuleGroupPath(module, entityStore)

  override fun getModuleGrouper(model: ModifiableModuleModel?): ModuleGrouper = createGrouper(project, model)

  override fun loadModule(file: Path): Module {
    return loadModule(file.systemIndependentPath)
  }

  override fun loadModule(filePath: String): Module {
    val model = modifiableModel
    val module = model.loadModule(filePath)
    model.commit()
    return module
  }

  override fun getUnloadedModuleDescription(moduleName: String): UnloadedModuleDescription? = unloadedModules[moduleName]

  private val modulesArrayValue = CachedValue<Array<Module>> { storage ->
    modules(storage).toList().toTypedArray()
  }

  override fun getModules(): Array<Module> = entityStore.cachedValue(modulesArrayValue)

  private val sortedModulesValue = CachedValue<Array<Module>> { storage ->
    val allModules = modules(storage).toList().toTypedArray<Module>()
    Arrays.sort(allModules, moduleDependencyComparator())
    allModules
  }

  override fun getSortedModules(): Array<Module> = entityStore.cachedValue(sortedModulesValue)

  override fun findModuleByName(name: String): Module? {
    val entity = entityStore.current.resolve(ModuleId(name)) ?: return null
    return entityStore.current.findModuleByEntity(entity)
  }

  override fun disposeModule(module: Module) = ApplicationManager.getApplication().runWriteAction {
    val modifiableModel = modifiableModel
    modifiableModel.disposeModule(module)
    modifiableModel.commit()
  }

  override fun setUnloadedModules(unloadedModuleNames: List<String>) {
    if (unloadedModules.keys == unloadedModuleNames.toSet()) {
      //optimization
      return
    }

    UnloadedModulesListStorage.getInstance(project).unloadedModuleNames = unloadedModuleNames

    if (unloadedModuleNames.isNotEmpty()) {
      val loadedModules = modules.map { it.name }.toMutableList()
      loadedModules.removeAll(unloadedModuleNames)
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(loadedModules)
    }

    val unloadedModuleNamesSet = unloadedModuleNames.toSet()
    val moduleMap = entityStore.current.moduleMap
    val modulesToUnload = entityStore.current.entities(ModuleEntity::class.java)
      .filter { it.name in unloadedModuleNamesSet }
      .mapNotNull { moduleEntity ->
        val module = moduleMap.getDataByEntity(moduleEntity)
        module?.let { Pair(moduleEntity, module) }
      }
      .toList()
    val moduleEntitiesToLoad = entityStore.current.entities(ModuleEntity::class.java)
      .filter { moduleMap.getDataByEntity(it) == null && it.name !in unloadedModuleNamesSet }.toList()

    unloadedModules.keys.removeAll { it !in unloadedModuleNamesSet }
    runWriteAction {
      if (modulesToUnload.isNotEmpty()) {
        // we need to save module configurations before unloading, otherwise their settings will be lost
        saveComponentManager(project)
      }

      ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
        {
          for ((moduleEntity, module) in modulesToUnload) {
            fireBeforeModuleRemoved(module)

            val description = LoadedModuleDescriptionImpl(module)
            val modulePath = getModulePath(module, entityStore)
            val pointerManager = VirtualFilePointerManager.getInstance()
            val contentRoots = ModuleRootManager.getInstance(module).contentRootUrls.map { url ->
              pointerManager.create(url, this, null)
            }
            val unloadedModuleDescription = UnloadedModuleDescriptionImpl(modulePath, description.dependencyModuleNames, contentRoots)
            unloadedModules[module.name] = unloadedModuleDescription
            WorkspaceModel.getInstance(project).updateProjectModelSilent {
              it.mutableModuleMap.removeMapping(moduleEntity)
            }
            fireEventAndDisposeModule(module)
          }

          loadModules(moduleEntitiesToLoad)
        }, false, true)
    }
  }

  override fun removeUnloadedModules(unloadedModules: MutableCollection<out UnloadedModuleDescription>) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    unloadedModules.forEach { this.unloadedModules.remove(it.name) }

    UnloadedModulesListStorage.getInstance(project).unloadedModuleNames = this.unloadedModules.keys.toList()
  }

  internal fun getModuleVirtualFileUrl(moduleEntity: ModuleEntity): VirtualFileUrl? {
    val entitySource = when (val moduleSource = moduleEntity.entitySource) {
      is JpsFileDependentEntitySource -> moduleSource.originalSource
      is CustomModuleEntitySource -> moduleSource.internalSource
      else -> moduleEntity.entitySource
    }
    if (entitySource !is JpsFileEntitySource.FileInDirectory) {
      return null
    }
    return entitySource.directory.append("${moduleEntity.name}.iml")
  }

  fun createModuleInstance(moduleEntity: ModuleEntity,
                           versionedStorage: VersionedEntityStorage,
                           diff: WorkspaceEntityStorageDiffBuilder?,
                           isNew: Boolean): ModuleBridge {
    val moduleFileUrl = getModuleVirtualFileUrl(moduleEntity)
    val module = ModuleBridgeImpl(
      name = moduleEntity.name,
      project = project,
      virtualFileUrl = moduleFileUrl,
      moduleEntityId = moduleEntity.persistentId(),
      entityStorage = versionedStorage,
      diff = diff
    )

    module.init {
      if (moduleFileUrl != null) {
        try {
          val moduleStore = module.stateStore as ModuleStore
          moduleStore.setPath(moduleFileUrl.toPath(), null, isNew)
        }
        catch (t: Throwable) {
          logger<ModuleManagerComponentBridge>().error(t)
        }
      }
    }

    return module
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ModuleManagerComponentBridge {
      return ModuleManager.getInstance(project) as ModuleManagerComponentBridge
    }

    private fun EntityChange<LibraryEntity>.isModuleLibrary(): Boolean = when (this) {
      is EntityChange.Added -> entity.tableId is LibraryTableId.ModuleLibraryTableId
      is EntityChange.Removed -> entity.tableId is LibraryTableId.ModuleLibraryTableId
      is EntityChange.Replaced -> oldEntity.tableId is LibraryTableId.ModuleLibraryTableId
    }

    private fun List<EntityChange<LibraryEntity>>.filterModuleLibraryChanges() = filter { it.isModuleLibrary() }

    internal fun getModuleGroupPath(module: Module, entityStorage: VersionedEntityStorage): Array<String>? {
      val moduleEntity = entityStorage.current.findModuleEntity(module as ModuleBridge) ?: return null
      return moduleEntity.groupPath?.path?.toTypedArray()
    }

    internal fun getModulePath(module: Module, entityStorage: VersionedEntityStorage): ModulePath = ModulePath(
      path = module.moduleFilePath,
      group = getModuleGroupPath(module, entityStorage)?.joinToString(separator = MODULE_GROUP_SEPARATOR)
    )

    internal fun hasModuleGroups(entityStorage: VersionedEntityStorage) =
      entityStorage.current.entities(ModuleGroupPathEntity::class.java).firstOrNull() != null

    private const val MODULE_BRIDGE_MAPPING_ID = "intellij.modules.bridge"

    internal val WorkspaceEntityStorage.moduleMap: ExternalEntityMapping<ModuleBridge>
      get() = getExternalMapping(MODULE_BRIDGE_MAPPING_ID)
    internal val WorkspaceEntityStorageDiffBuilder.mutableModuleMap: MutableExternalEntityMapping<ModuleBridge>
      get() = getMutableExternalMapping(MODULE_BRIDGE_MAPPING_ID)

    @JvmStatic
    fun WorkspaceEntityStorage.findModuleEntity(module: ModuleBridge) =
      moduleMap.getEntities(module).firstOrNull() as ModuleEntity?

    @JvmStatic
    fun WorkspaceEntityStorage.findModuleByEntity(entity: ModuleEntity): ModuleBridge? = moduleMap.getDataByEntity(entity)

    private val dependencyGraphWithTestsValue = CachedValue { storage ->
      buildModuleGraph(storage, true)
    }
    private val dependencyGraphWithoutTestsValue = CachedValue { storage ->
      buildModuleGraph(storage, false)
    }
    private val dependencyComparatorValue = CachedValue { storage ->
      DFSTBuilder(buildModuleGraph(storage, true)).comparator()
    }

    @JvmStatic
    fun changeModuleEntitySource(module: ModuleBridge, moduleEntityStore: WorkspaceEntityStorage, newSource: EntitySource,
                                 moduleDiff: WorkspaceEntityStorageDiffBuilder?) {
      val oldEntitySource = moduleEntityStore.findModuleEntity(module)?.entitySource ?: return
      fun changeSources(diffBuilder: WorkspaceEntityStorageDiffBuilder, storage: WorkspaceEntityStorage) {
        val entitiesMap = storage.entitiesBySource { it == oldEntitySource }
        entitiesMap.values.asSequence().flatMap { it.values.asSequence().flatten() }.forEach {
          if (it !is FacetEntity) {
            diffBuilder.changeSource(it, newSource)
          }
        }
      }

      if (moduleDiff != null) {
        changeSources(moduleDiff, moduleEntityStore)
      }
      else {
        WriteAction.runAndWait<RuntimeException> {
          WorkspaceModel.getInstance(module.project).updateProjectModel { builder ->
            changeSources(builder, builder)
          }
        }
      }
    }

    private fun buildModuleGraph(storage: WorkspaceEntityStorage, includeTests: Boolean): Graph<Module> {
      return GraphGenerator.generate(CachingSemiGraph.cache(object : InboundSemiGraph<Module> {
        override fun getNodes(): Collection<Module> {
          return modules(storage).toList()
        }

        override fun getIn(m: Module): Iterator<Module> {
          val moduleMap = storage.moduleMap
          val entity = moduleMap.getEntities(m as ModuleBridge).firstOrNull() as ModuleEntity?
          return (entity?.dependencies?.asSequence() ?: emptySequence())
            .filterIsInstance<ModuleDependencyItem.Exportable.ModuleDependency>()
            .filter { includeTests || it.scope != ModuleDependencyItem.DependencyScope.TEST }
            .mapNotNull { it.module.resolve(storage) }
            .mapNotNull { moduleMap.getDataByEntity(it) }
            .iterator()
        }
      }))
    }

    private fun modules(storage: WorkspaceEntityStorage): Sequence<ModuleBridge> {
      val moduleMap = storage.moduleMap
      return storage.entities(ModuleEntity::class.java).mapNotNull { moduleMap.getDataByEntity(it) }
    }
  }
}
