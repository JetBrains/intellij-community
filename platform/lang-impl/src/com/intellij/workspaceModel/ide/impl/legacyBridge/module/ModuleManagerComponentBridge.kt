// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.ProjectTopics
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.AutomaticModuleUnloader
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.openapi.module.impl.NonPersistentModuleStore
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.jps.serialization.ErrorReporter
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectEntitiesLoader
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetEntityChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path

class ModuleManagerComponentBridge(private val project: Project) : ModuleManagerBridgeImpl(project) {
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)

  internal class ModuleManagerInitProjectActivity : InitProjectActivity {
    override suspend fun run(project: Project) {
      val moduleManager = ModuleManager.getInstance(project) as ModuleManagerComponentBridge
      var activity = StartUpMeasurer.startActivity("firing modules_added event", ActivityCategory.DEFAULT)
      val modules = moduleManager.modules().toList()
      moduleManager.fireModulesAdded(modules)

      activity = activity.endAndStart("deprecated module component moduleAdded calling")
      @Suppress("removal", "DEPRECATION")
      val deprecatedComponents = mutableListOf<com.intellij.openapi.module.ModuleComponent>()
      for (module in modules) {
        if (!module.isLoaded) {
          module.moduleAdded(deprecatedComponents)
        }
      }
      if (!deprecatedComponents.isEmpty()) {
        withContext(Dispatchers.EDT) {
          ApplicationManager.getApplication().runWriteAction {
            for (deprecatedComponent in deprecatedComponents) {
              @Suppress("DEPRECATION", "removal")
              deprecatedComponent.moduleAdded()
            }
          }
        }
      }
      activity.end()
    }
  }

  init {
    // default project doesn't have modules
    if (!project.isDefault) {
      val busConnection = project.messageBus.connect(this)
      busConnection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosed(eventProject: Project) {
          if (project == eventProject) {
            for (module in modules()) {
              module.projectClosed()
            }
          }
        }
      })

      val rootsChangeListener = ProjectRootsChangeListener(project)
      busConnection.subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
        override fun beforeChanged(event: VersionedStorageChange) {
          if (!VirtualFileUrlWatcher.getInstance(project).isInsideFilePointersUpdate) {
            //the old implementation doesn't fire rootsChanged event when roots are moved or renamed, let's keep this behavior for now
            rootsChangeListener.beforeChanged(event)
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
        }

        override fun changed(event: VersionedStorageChange) {
          val moduleLibraryChanges = event.getChanges(LibraryEntity::class.java).filterModuleLibraryChanges()
          val changes = event.getChanges(ModuleEntity::class.java)
          if (changes.isNotEmpty() || moduleLibraryChanges.isNotEmpty()) {
            LOG.debug("Process changed modules and facets")
            incModificationCount()
            for (change in moduleLibraryChanges) {
              when (change) {
                is EntityChange.Removed -> processModuleLibraryChange(change, event)
                is EntityChange.Replaced -> processModuleLibraryChange(change, event)
                is EntityChange.Added -> Unit
              }
            }

            val oldModuleNames = mutableMapOf<Module, String>()
            val unloadedModulesSet = UnloadedModulesListStorage.getInstance(project).unloadedModuleNames
            for (change in changes) {
              processModuleChange(change, unloadedModulesSet, oldModuleNames, event)
            }

            for (change in moduleLibraryChanges) {
              if (change is EntityChange.Added) processModuleLibraryChange(change, event)
            }
            // After every change processed
            postProcessModules(oldModuleNames)
            incModificationCount()
          }
          // Roots changed should be sent after syncing with legacy bridge
          if (!VirtualFileUrlWatcher.getInstance(project).isInsideFilePointersUpdate) {
            //the old implementation doesn't fire rootsChanged event when roots are moved or renamed, let's keep this behavior for now
            rootsChangeListener.changed(event)
          }
        }
      })
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
    val unloadedModuleNames = UnloadedModulesListStorage.getInstance(project).unloadedModuleNames
    if (change is EntityChange.Added) {
      val alreadyCreatedModule = change.entity.findModule(builder)
      if (alreadyCreatedModule == null) {
        if (change.entity.name in unloadedModuleNames) {
          return
        }

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
      if (moduleEntity.name !in unloadedModules) {

        val library = builder.libraryMap.getDataByEntity(change.entity)
        if (library == null) {
          val module = moduleEntity.findModule(builder)
                       ?: error("Could not find module bridge for module entity $moduleEntity")
          val moduleRootComponent = ModuleRootComponentBridge.getInstance(module)
          (moduleRootComponent.getModuleLibraryTable() as ModuleLibraryTableBridgeImpl).addLibrary(change.entity, builder)
        }
      }
    }
  }

  private fun postProcessModules(oldModuleNames: MutableMap<Module, String>) {
    if (oldModuleNames.isNotEmpty()) {
      project.messageBus
        .syncPublisher(ProjectTopics.MODULES)
        .modulesRenamed(project, oldModuleNames.keys.toList()) { module -> oldModuleNames[module] }
    }

    if (unloadedModules.isNotEmpty()) {
      AutomaticModuleUnloader.getInstance(project).setLoadedModules(modules.map { it.name })
    }
  }

  private fun processModuleChange(change: EntityChange<ModuleEntity>, unloadedModuleNames: Set<String>,
                                  oldModuleNames: MutableMap<Module, String>, event: VersionedStorageChange) {
    when (change) {
      is EntityChange.Removed -> {
        // It's possible case then idToModule doesn't contain element e.g. if unloaded module was removed
        val module = change.entity.findModule(event.storageBefore)
        if (module != null) {
          fireEventAndDisposeModule(module)
        }
      }

      is EntityChange.Added -> {
        val alreadyCreatedModule = change.entity.findModule(event.storageAfter)
        val module = if (alreadyCreatedModule != null) {
          unloadedModules.remove(change.entity.name)

          alreadyCreatedModule.entityStorage = entityStore
          alreadyCreatedModule.diff = null
          alreadyCreatedModule
        }
        else {
          if (change.entity.name in unloadedModuleNames) {
            unloadedModules[change.entity.name] = UnloadedModuleDescriptionBridge.createDescription(change.entity)
            return
          }
          error("Module bridge should already be created")
        }

        if (project.isOpen) {
          fireModuleAddedInWriteAction(module)
        }
      }

      is EntityChange.Replaced -> {
        val oldId = change.oldEntity.symbolicId
        val newId = change.newEntity.symbolicId

        if (oldId != newId) {
          unloadedModules.remove(change.newEntity.name)
          val module = change.oldEntity.findModule(event.storageBefore)
          if (module != null) {
            module.rename(newId.name, getModuleVirtualFileUrl(change.newEntity), true)
            oldModuleNames[module] = oldId.name
          }
        }
        else if (getImlFileDirectory(change.oldEntity) != getImlFileDirectory(change.newEntity)) {
          val module = change.newEntity.findModule(event.storageBefore)
          val imlFilePath = getModuleVirtualFileUrl(change.newEntity)
          if (module != null && imlFilePath != null) {
            module.onImlFileMoved(imlFilePath)
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
        val idBefore = change.oldEntity.symbolicId
        val idAfter = change.newEntity.symbolicId

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
                           ?: error("Could not find module for module library: ${change.entity.symbolicId}")
        if (moduleEntity.name !in unloadedModules) {

          val library = event.storageAfter.libraryMap.getDataByEntity(change.entity)
          if (library != null) {
            (library as LibraryBridgeImpl).entityStorage = entityStore
            library.clearTargetBuilder()
          }
        }
      }
    }
  }

  private fun List<EntityChange<LibraryEntity>>.filterModuleLibraryChanges() = filter { it.isModuleLibrary() }

  private fun fireModuleAddedInWriteAction(module: ModuleEx) {
    ApplicationManager.getApplication().runWriteAction {
      if (!module.isLoaded) {
        @Suppress("removal", "DEPRECATION")
        val oldComponents = mutableListOf<com.intellij.openapi.module.ModuleComponent>()
        module.moduleAdded(oldComponents)
        for (oldComponent in oldComponents) {
          @Suppress("DEPRECATION", "removal")
          oldComponent.moduleAdded()
        }
        fireModulesAdded(listOf(module))
      }
    }
  }

  private fun fireModulesAdded(modules: List<Module>) {
    project.messageBus.syncPublisher(ProjectTopics.MODULES).modulesAdded(project, modules)
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
    JpsProjectEntitiesLoader.loadModule(Path.of(filePath), getJpsProjectConfigLocation(project)!!, builder, object : ErrorReporter {
      override fun reportError(message: String, file: VirtualFileUrl) {
        errorMessage = message
      }
    }, virtualFileManager)
    if (errorMessage != null) {
      throw IOException("Failed to load module from $filePath: $errorMessage")
    }
    diff.addDiff(builder)
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
    return ModuleBridgeImpl(symbolicId, name, project, virtualFileUrl, entityStorage, diff)
  }

  companion object {
    private val LOG = logger<ModuleManagerComponentBridge>()

    private fun EntityChange<LibraryEntity>.isModuleLibrary(): Boolean {
      return when (this) {
        is EntityChange.Added -> entity.tableId is LibraryTableId.ModuleLibraryTableId
        is EntityChange.Removed -> entity.tableId is LibraryTableId.ModuleLibraryTableId
        is EntityChange.Replaced -> oldEntity.tableId is LibraryTableId.ModuleLibraryTableId
      }
    }
  }
}
