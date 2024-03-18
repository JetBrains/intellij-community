// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.module.ModuleComponent
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.NonPersistentModuleStore
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.impl.span
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
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.workspaceModel.ide.getJpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.jps.serialization.BaseIdeSerializationContext
import com.intellij.workspaceModel.ide.impl.jps.serialization.CachingJpsFileContentReader
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetEntityChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ModuleRootListenerBridgeImpl
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.toPath
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.nio.file.Path


internal class ModuleManagerComponentBridge(private val project: Project, coroutineScope: CoroutineScope)
  : ModuleManagerBridgeImpl(project = project, coroutineScope = coroutineScope, moduleRootListenerBridge = ModuleRootListenerBridgeImpl) {
  private val virtualFileManager: VirtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

  internal class ModuleManagerInitProjectActivity : InitProjectActivity {
    override suspend fun run(project: Project) {
      val moduleManager = project.serviceAsync<ModuleManager>() as ModuleManagerComponentBridge
      val modules = moduleManager.modules().toList()
      span("firing modules_added event") {
        blockingContext {
          fireModulesAdded(project, modules)
        }
      }
      span("deprecated module component moduleAdded calling") {
        @Suppress("removal", "DEPRECATION")
        val deprecatedComponents = mutableListOf<ModuleComponent>()
        for (module in modules) {
          if (!module.isLoaded) {
            module.moduleAdded(deprecatedComponents)
          }
        }
        if (!deprecatedComponents.isEmpty()) {
          writeAction {
            for (deprecatedComponent in deprecatedComponents) {
              @Suppress("DEPRECATION", "removal")
              deprecatedComponent.moduleAdded()
            }
          }
        }
      }
    }
  }

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
    val moduleChanges = (event[ModuleEntity::class.java] as? List<EntityChange<ModuleEntity>>) ?: emptyList()
    for (moduleChange in moduleChanges) {
      initializeModuleBridge(moduleChange, builder)
    }

    // Initialize facets
    FacetEntityChangeListener.getInstance(project).initializeFacetBridge(event, builder)

    // Initialize module libraries
    val moduleLibraryChanges = ((event[LibraryEntity::class.java] as? List<EntityChange<LibraryEntity>>) ?: emptyList())
      .filterModuleLibraryChanges()
    for (change in moduleLibraryChanges) {
      initializeModuleLibraryBridge(change, builder)
    }
  }

  private fun initializeModuleBridge(change: EntityChange<ModuleEntity>, builder: MutableEntityStorage) {
    if (change is EntityChange.Added) {
      val alreadyCreatedModule = change.entity.findModule(builder)
      if (alreadyCreatedModule == null) {
        // Create module bridge
        val plugins = PluginManagerCore.getPluginSet().getEnabledModules()
        val module = createModuleInstance(moduleEntity = change.entity,
                                          versionedStorage = entityStore,
                                          diff = builder,
                                          isNew = true,
                                          precomputedExtensionModel = null,
                                          plugins = plugins,
                                          corePlugin = plugins.firstOrNull { it.pluginId == PluginManagerCore.CORE_ID })
        builder.mutableModuleMap.addMapping(change.entity, module)
      }
    }
  }

  private fun initializeModuleLibraryBridge(change: EntityChange<LibraryEntity>, builder: MutableEntityStorage) {
    if (change is EntityChange.Added) {
      val tableId = change.entity.tableId as LibraryTableId.ModuleLibraryTableId
      val moduleEntity = builder.resolve(tableId.moduleId)
                         ?: error("Could not find module for module library: ${change.entity.symbolicId}")
      val library = builder.libraryMap.getDataByEntity(change.entity)
      if (library == null) {
        val module = moduleEntity.findModule(builder)
                     ?: error("Could not find module bridge for module entity $moduleEntity")
        val moduleRootComponent = ModuleRootComponentBridge.getInstance(module)
        (moduleRootComponent.getModuleLibraryTable() as ModuleLibraryTableBridgeImpl).addLibrary(change.entity, builder)
      }
    }
  }

  override fun registerNonPersistentModuleStore(module: ModuleBridge) {
    (module as ModuleBridgeImpl).registerService(serviceInterface = IComponentStore::class.java,
                                                 implementation = NonPersistentModuleStore::class.java,
                                                 pluginDescriptor = ComponentManagerImpl.fakeCorePluginDescriptor,
                                                 override = true)
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

    val moduleFileUrl = getModuleVirtualFileUrl(moduleEntity)!!
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(moduleFileUrl.toPath())
    return moduleEntity
  }

  override fun createModule(symbolicId: ModuleId, name: String, virtualFileUrl: VirtualFileUrl?, entityStorage: VersionedEntityStorage,
                            diff: MutableEntityStorage?): ModuleBridge {
    return ModuleBridgeImpl(moduleEntityId = symbolicId,
                            name = name,
                            project = project,
                            virtualFileUrl = virtualFileUrl,
                            entityStorage = entityStorage,
                            diff = diff)
  }
}

private class SingleImlSerializationContext(override val virtualFileUrlManager: VirtualFileUrlManager,
                                            override val fileContentReader: JpsFileContentReader) : BaseIdeSerializationContext() {
  override val isExternalStorageEnabled: Boolean
    get() = false
  override val fileInDirectorySourceNames: FileInDirectorySourceNames
    get() = FileInDirectorySourceNames.empty()
}
