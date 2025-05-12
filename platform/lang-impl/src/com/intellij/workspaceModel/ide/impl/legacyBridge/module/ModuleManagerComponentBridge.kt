// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ModuleComponentManager
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
import com.intellij.workspaceModel.ide.impl.jps.serialization.BaseIdeSerializationContext
import com.intellij.workspaceModel.ide.impl.jps.serialization.CachingJpsFileContentReader
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetEntityChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.fireModulesAdded
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ModuleRootListenerBridgeImpl
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.toPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

private val LOG = logger<ModuleManagerComponentBridge>()


private class ModuleManagerInitProjectActivity : InitProjectActivity {
  override suspend fun run(project: Project) {
    val modules = (project.serviceAsync<ModuleManager>() as ModuleManagerComponentBridge).modules().toList()
    coroutineContext.ensureActive()
    span("firing modules_added event") {
      fireModulesAdded(project, modules)
    }
    span("deprecated module component moduleAdded calling") {
      for (module in modules) {
        module.markAsLoaded()
      }
    }
  }
}

@ApiStatus.Internal
open class ModuleManagerComponentBridge(private val project: Project, coroutineScope: CoroutineScope)
  : ModuleManagerBridgeImpl(project = project, coroutineScope = coroutineScope, moduleRootListenerBridge = ModuleRootListenerBridgeImpl) {
  private val virtualFileManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

  init {
    // default project doesn't have facets
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

    // Theoretically, the module initialization can be parallelized using fork-join approach, see IJPL-149482
    //   This approach is used in ModuleManagerBridgeImpl.loadModules
    // However, simple use of Dispatchers.Default while being inside write action, may cause threading issues, see IDEA-355596
    val precomputedModel = if (moduleChanges.isNotEmpty()) {
      precomputeModuleLevelExtensionModel()
    }
    else {
      null
    }
    for (change in moduleChanges) {
      if (change !is EntityChange.Added<ModuleEntity>) {
        continue
      }
      if (change.newEntity.findModule(builder) != null) {
        continue
      }

      LOG.debug { "Creating module instance for ${change.newEntity.name}" }
      val plugins = PluginManagerCore.getPluginSet().getEnabledModules()
      val bridge = createModuleInstanceWithoutCreatingComponents(
        moduleEntity = change.newEntity,
        versionedStorage = entityStore,
        diff = builder,
        isNew = true,
        precomputedExtensionModel = precomputedModel!!,
        plugins = plugins,
      )
      LOG.debug { "Creating components ${change.newEntity.name}" }
      bridge.callCreateComponents()

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
    val configLocation = getJpsProjectConfigLocation(project)!!
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

  override fun createModule(
    symbolicId: ModuleId,
    name: String,
    virtualFileUrl: VirtualFileUrl?,
    entityStorage: VersionedEntityStorage,
    diff: MutableEntityStorage?,
    init: (ModuleBridge) -> Unit,
  ): ModuleBridge {
    val componentManager = ModuleComponentManager(project.getComponentManagerImpl())
    return ModuleBridgeImpl(
      moduleEntityId = symbolicId,
      name = name,
      project = project,
      virtualFileUrl = virtualFileUrl,
      entityStorage = entityStorage,
      diff = diff,
      componentManager = componentManager,
    ).also {
      componentManager.initForModule(it)
      init(it)
    }
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
