// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.ProjectTopics
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.ModuleStore
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.AutomaticModuleUnloader
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.openapi.module.impl.NonPersistentModuleStore
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.executeOrQueueOnDispatchThread
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
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import java.io.IOException
import java.nio.file.Paths

class ModuleManagerComponentBridge(private val project: Project) : ModuleManagerBridgeImpl(project) {
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)

  init {
    // default project doesn't have modules
    if (!project.isDefault) {
      val busConnection = project.messageBus.connect(this)
      busConnection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
        override fun projectOpened(eventProject: Project) {
          val activity = StartUpMeasurer.startActivity("firing modules_added event", ActivityCategory.DEFAULT)
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
          if (!VirtualFileUrlWatcher.getInstance(project).isInsideFilePointersUpdate) {
            //the old implementation doesn't fire rootsChanged event when roots are moved or renamed, let's keep this behavior for now
            rootsChangeListener.beforeChanged(event)
          }
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
              for (change in moduleLibraryChanges) {
                when (change) {
                  is EntityChange.Removed -> processModuleLibraryChange(change, event)
                  is EntityChange.Replaced -> processModuleLibraryChange(change, event)
                  is EntityChange.Added -> Unit
                }
              }

              for (change in facetChanges) {
                when (change) {
                  is EntityChange.Removed -> FacetEntityChangeListener.getInstance(project).processChange(change, event.storageBefore,
                                                                                                          addedModulesNames)
                  is EntityChange.Replaced -> FacetEntityChangeListener.getInstance(project).processChange(change, event.storageBefore,
                                                                                                           addedModulesNames)
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

              for (change in facetChanges) {
                if (change is EntityChange.Added) {
                  FacetEntityChangeListener.getInstance(project).processChange(change, event.storageBefore, addedModulesNames)
                }
              }

              // After every change processed
              postProcessModules(oldModuleNames)
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

  private fun addModule(moduleEntity: ModuleEntity): ModuleBridge {
    val module = createModuleInstance(moduleEntity, entityStore, diff = null, isNew = true, null)
    WorkspaceModel.getInstance(project).updateProjectModelSilent {
      it.mutableModuleMap.addMapping(moduleEntity, module)
    }
    return module
  }

  private fun processModuleChange(change: EntityChange<ModuleEntity>, unloadedModuleNames: Set<String>,
                                  oldModuleNames: MutableMap<Module, String>, event: VersionedStorageChange) {
    when (change) {
      is EntityChange.Removed -> {
        // It's possible case then idToModule doesn't contain element e.g if unloaded module was removed
        val module = event.storageBefore.findModuleByEntity(change.entity)
        if (module != null) {
          fireEventAndDisposeModule(module)
        }
      }

      is EntityChange.Added -> {
        val alreadyCreatedModule = event.storageAfter.findModuleByEntity(change.entity)
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
          unloadedModules.remove(change.newEntity.name)
          val module = event.storageBefore.findModuleByEntity(change.oldEntity)
          if (module != null) {
            module.rename(newId.name, getModuleVirtualFileUrl(change.newEntity), true)
            oldModuleNames[module] = oldId.name
          }
        }
        else if (getImlFileDirectory(change.oldEntity) != getImlFileDirectory(change.newEntity)) {
          val module = event.storageBefore.findModuleByEntity(change.newEntity)
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
            (moduleRootComponent.getModuleLibraryTable() as ModuleLibraryTableBridgeImpl).addLibrary(change.entity, null)
          }
          if (library != null) {
            (library as LibraryBridgeImpl).entityStorage = entityStore
            library.clearTargetBuilder()
          }
        }
      }
    }
  }

  private fun List<EntityChange<LibraryEntity>>.filterModuleLibraryChanges() = filter { it.isModuleLibrary() }

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

  override fun registerNonPersistentModuleStore(module: ModuleBridge) {
    (module as ModuleBridgeImpl).registerService(serviceInterface = IComponentStore::class.java,
                                                 implementation = NonPersistentModuleStore::class.java,
                                                 pluginDescriptor = ComponentManagerImpl.fakeCorePluginDescriptor,
                                                 override = true,
                                                 preloadMode = ServiceDescriptor.PreloadMode.FALSE)
  }

  override fun loadModuleToBuilder(moduleName: String, filePath: String, diff: WorkspaceEntityStorageBuilder): ModuleEntity {
    val builder = WorkspaceEntityStorageBuilder.create()
    var errorMessage: String? = null
    JpsProjectEntitiesLoader.loadModule(Paths.get(filePath), getJpsProjectConfigLocation(project)!!, builder, object : ErrorReporter {
      override fun reportError(message: String, file: VirtualFileUrl) {
        errorMessage = message
      }
    }, virtualFileManager)
    if (errorMessage != null) {
      throw IOException("Failed to load module from $filePath: $errorMessage")
    }
    diff.addDiff(builder)
    val moduleEntity = diff.entities(ModuleEntity::class.java).find { it.name == moduleName }
    if (moduleEntity == null) {
      throw IOException("Failed to load module from $filePath")
    }

    val moduleFileUrl = getModuleVirtualFileUrl(moduleEntity)!!
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(moduleFileUrl.toPath())
    return moduleEntity
  }

  override fun createModule(persistentId: ModuleId, name: String, virtualFileUrl: VirtualFileUrl?, entityStorage: VersionedEntityStorage,
                            diff: WorkspaceEntityStorageDiffBuilder?): ModuleBridge {
    return ModuleBridgeImpl(persistentId, name, project, virtualFileUrl, entityStorage, diff)
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
