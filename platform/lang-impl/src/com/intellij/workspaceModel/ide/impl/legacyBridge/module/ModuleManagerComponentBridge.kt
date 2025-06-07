// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.facet.impl.FacetEventsPublisher
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ModuleComponentManager
import com.intellij.openapi.project.ModuleListener.TOPIC
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.workspace.jps.OrphanageWorkerEntitySource
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.serialization.impl.ErrorReporter
import com.intellij.platform.workspace.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectEntitiesLoader
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.serviceContainer.getComponentManagerImpl
import com.intellij.serviceContainer.precomputeModuleLevelExtensionModel
import com.intellij.workspaceModel.ide.getJpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.VirtualFileUrlBridge
import com.intellij.workspaceModel.ide.impl.jps.serialization.BaseIdeSerializationContext
import com.intellij.workspaceModel.ide.impl.jps.serialization.CachingJpsFileContentReader
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetEntityChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ModuleRootListenerBridgeImpl
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.toPath
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path

private val LOG = logger<ModuleManagerComponentBridge>()

private class ModuleManagerInitProjectActivity : InitProjectActivity {
  override suspend fun run(project: Project) {
    val moduleManager = project.serviceAsync<ModuleManager>() as ModuleManagerComponentBridge
    val modules = moduleManager.modules().toList()
    span("firing modules_added event") {
      project.messageBus.syncPublisher(TOPIC).modulesAdded(project, modules)
    }
    for (module in modules) {
      module.markAsLoaded()
    }

    // listen only after we executed `fireModulesAdded`
    project.serviceAsync<FacetEventsPublisher>().listen()
  }
}

@ApiStatus.Internal
open class ModuleManagerComponentBridge(private val project: Project, coroutineScope: CoroutineScope)
  : ModuleManagerBridgeImpl(project = project, coroutineScope = coroutineScope, moduleRootListenerBridge = ModuleRootListenerBridgeImpl) {
    init {
    // a default project doesn't have facets
    if (!project.isDefault) {
      // Instantiate facet change listener as early as possible
      project.service<FacetEntityChangeListener>()
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun initializeBridges(event: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    // Initialize modules
    initializeModuleBridges(event, builder)

    // Initialize facets
    project.service<FacetEntityChangeListener>().initializeFacetBridge(event, builder)

    // Initialize module libraries
    val moduleLibraryChanges = ((event[LibraryEntity::class.java] as? List<EntityChange<LibraryEntity>>) ?: emptyList())
      .filterModuleLibraryChanges()
    for (change in moduleLibraryChanges) {
      initializeModuleLibraryBridge(change, builder)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun initializeModuleBridges(event: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    val moduleChanges = (event[ModuleEntity::class.java] as? List<EntityChange<ModuleEntity>>) ?: emptyList()
    LOG.debug { "Starting initialize bridges for ${moduleChanges.size} modules" }

    if (moduleChanges.isEmpty()) {
      return
    }

    val precomputedModel = precomputeModuleLevelExtensionModel()
    for (change in moduleChanges) {
      if (change !is EntityChange.Added<ModuleEntity>) {
        continue
      }
      if (change.newEntity.findModule(builder) != null) {
        continue
      }

      LOG.debug { "Creating module instance for ${change.newEntity.name}" }
      val bridge = createModuleInstanceWithoutCreatingComponents(
        moduleEntity = change.newEntity,
        versionedStorage = entityStore,
        diff = builder,
        isNew = true,
        precomputedExtensionModel = precomputedModel,
      )

      LOG.debug { "${change.newEntity.name} module initialized" }
      builder.mutableModuleMap.addMapping(change.newEntity, bridge)
    }
  }

  private fun initializeModuleLibraryBridge(change: EntityChange<LibraryEntity>, builder: MutableEntityStorage) {
    if (change is EntityChange.Added) {
      val tableId = change.newEntity.tableId as LibraryTableId.ModuleLibraryTableId
      val moduleEntity = builder.resolve(tableId.moduleId)
                         ?: error("Could not find module for module library: ${change.newEntity.symbolicId}")
      val library = builder.libraryMap.getDataByEntity(change.newEntity)
      if (library == null) {
        val module = moduleEntity.findModule(builder)
                     ?: error("Could not find module bridge for module entity $moduleEntity")
        val moduleRootComponent = ModuleRootComponentBridge.getInstance(module)
        (moduleRootComponent.getModuleLibraryTable() as ModuleLibraryTableBridgeImpl).addLibrary(change.newEntity, builder)
      }
    }
  }

  override fun loadModuleToBuilder(moduleName: String, filePath: String, diff: MutableEntityStorage): ModuleEntity {
    val builder = MutableEntityStorage.create()
    var errorMessage: String? = null
    val virtualFileManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val configLocation = getJpsProjectConfigLocation(project, virtualFileManager)!!
    val context = SingleImlSerializationContext(virtualFileManager, CachingJpsFileContentReader(configLocation))
    JpsProjectEntitiesLoader.loadModule(Path.of(filePath), configLocation, builder, object : ErrorReporter {
      override fun reportError(message: String, file: VirtualFileUrl) {
        errorMessage = message
      }
    }, context)
    if (errorMessage != null) {
      throw IOException("Failed to load module from $filePath: $errorMessage")
    }
    diff.applyChangesFrom(builder)
    val moduleEntity = diff.entities(ModuleEntity::class.java).firstOrNull { it.name == moduleName }
    if (moduleEntity == null) {
      throw IOException("Failed to load module from $filePath")
    }

    if (moduleEntity.entitySource is OrphanageWorkerEntitySource) {
      throw IOException("The file only declares additional module components, but not the module itself: $filePath")
    }

    val moduleFileUrl = getModuleVirtualFileUrl(moduleEntity)!!
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(moduleFileUrl.toPath())
    return moduleEntity
  }

  final override fun initFacets(modules: Collection<Pair<ModuleEntity, ModuleBridge>>) {
    coroutineScope.launch(CoroutineName("init facets")) {
      ModuleBridgeImpl.initFacets(modules = modules, project = project)
    }
  }

  override fun createModule(
    symbolicId: ModuleId,
    name: String,
    virtualFileUrl: VirtualFileUrl?,
    entityStorage: VersionedEntityStorage,
    diff: MutableEntityStorage?,
    init: (ModuleBridge) -> Unit,
  ): ModuleBridge {
    val componentManager = ModuleComponentManager(project.getComponentManagerImpl())
    val moduleBridge = ModuleBridgeImpl(
      moduleEntityId = symbolicId,
      name = name,
      project = project,
      virtualFileUrl = virtualFileUrl as? VirtualFileUrlBridge,
      entityStorage = entityStorage,
      diff = diff,
      componentManager = componentManager,
    )
    componentManager.initForModule(moduleBridge)
    init(moduleBridge)
    return moduleBridge
  }
}

private class SingleImlSerializationContext(
  override val virtualFileUrlManager: VirtualFileUrlManager,
  override val fileContentReader: JpsFileContentReader,
) : BaseIdeSerializationContext() {
  override val isExternalStorageEnabled: Boolean
    get() = false
  override val fileInDirectorySourceNames: FileInDirectorySourceNames
    get() = FileInDirectorySourceNames.empty()
}
