// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.configurationStore.RenameableStateStorageManager
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleImpl
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.VirtualFileUrlBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.toPath
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnStorage
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

internal class ModuleBridgeImpl(
  override var moduleEntityId: ModuleId,
  name: String,
  project: Project,
  virtualFileUrl: VirtualFileUrl?,
  override var entityStorage: VersionedEntityStorage,
  override var diff: WorkspaceEntityStorageDiffBuilder?
) : ModuleImpl(name, project, virtualFileUrl as? VirtualFileUrlBridge), ModuleBridge {
  init {
    // default project doesn't have modules
    if (!project.isDefault && !project.isDisposed) {
      val busConnection = project.messageBus.connect(this)

      WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, object : WorkspaceModelChangeListener {
        override fun beforeChanged(event: VersionedStorageChange) {
          event.getChanges(ModuleEntity::class.java).filterIsInstance<EntityChange.Removed<ModuleEntity>>().forEach {
            if (it.entity.persistentId() != moduleEntityId) return@forEach

            if (event.storageBefore.moduleMap.getDataByEntity(it.entity) != this@ModuleBridgeImpl) return@forEach

            val currentStore = entityStorage.current
            val storage = if (currentStore is WorkspaceEntityStorageBuilder) currentStore.toStorage() else currentStore
            entityStorage = VersionedEntityStorageOnStorage(storage)
            assert(entityStorage.current.resolve(moduleEntityId) != null) {
              // If we ever get this assertion, replace use `event.storeBefore` instead of current
              // As it made in ArtifactBridge
              "Cannot resolve module $moduleEntityId. Current store: $currentStore"
            }
          }
        }
      })
    }
  }

  override fun rename(newName: String, newModuleFileUrl: VirtualFileUrl?, notifyStorage: Boolean) {
    myImlFilePointer = newModuleFileUrl as VirtualFileUrlBridge
    rename(newName, notifyStorage)
  }

  override fun rename(newName: String, notifyStorage: Boolean) {
    moduleEntityId = moduleEntityId.copy(name = newName)
    super<ModuleImpl>.rename(newName, notifyStorage)
  }

  override fun onImlFileMoved(newModuleFileUrl: VirtualFileUrl) {
    myImlFilePointer = newModuleFileUrl as VirtualFileUrlBridge
    val imlPath = newModuleFileUrl.toPath()
    (store.storageManager as RenameableStateStorageManager).pathRenamed(imlPath, null)
    store.setPath(imlPath)
    (PathMacroManager.getInstance(this) as? ModulePathMacroManager)?.onImlFileMoved()
  }

  override fun registerComponents(modules: Sequence<IdeaPluginDescriptorImpl>,
                                  app: Application?,
                                  precomputedExtensionModel: PrecomputedExtensionModel?,
                                  listenerCallbacks: List<Runnable>?) {
    registerComponents(corePlugin = modules.find { it.pluginId == PluginManagerCore.CORE_ID },
                       modules = modules,
                       precomputedExtensionModel = precomputedExtensionModel,
                       app = app,
                       listenerCallbacks = listenerCallbacks)
  }

  override fun callCreateComponents() {
    createComponents(null)
  }

  override fun registerComponents(corePlugin: IdeaPluginDescriptor?,
                                  modules: Sequence<IdeaPluginDescriptorImpl>,
                                  precomputedExtensionModel: PrecomputedExtensionModel?,
                                  app: Application?,
                                  listenerCallbacks: List<Runnable>?) {
    super.registerComponents(modules = modules,
                             app = app,
                             precomputedExtensionModel = precomputedExtensionModel,
                             listenerCallbacks = listenerCallbacks)
    if (corePlugin == null) {
      return
    }
    unregisterComponent(DeprecatedModuleOptionManager::class.java)

    try {
      //todo improve
      val classLoader = javaClass.classLoader
      val apiClass = classLoader.loadClass("com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager")
      val implClass = classLoader.loadClass("com.intellij.openapi.externalSystem.service.project.ExternalSystemModulePropertyManagerBridge")
      registerService(serviceInterface = apiClass, implementation = implClass, pluginDescriptor = corePlugin, override = true)
    }
    catch (ignored: Throwable) {
    }
  }

  override fun getOptionValue(key: String): String? {
    val moduleEntity = entityStorage.current.findModuleEntity(this)
    if (key == Module.ELEMENT_TYPE) {
      return moduleEntity?.type
    }
    return moduleEntity?.customImlData?.customModuleOptions?.get(key)
  }

  override fun setOption(key: String, value: String?) {
    fun updateOptionInEntity(diff: WorkspaceEntityStorageDiffBuilder, entity: ModuleEntity) {
      if (key == Module.ELEMENT_TYPE) {
        diff.modifyEntity(ModifiableModuleEntity::class.java, entity) { type = value }
      }
      else {
        val customImlData = entity.customImlData
        if (customImlData == null) {
          if (value != null) {
            diff.addModuleCustomImlDataEntity(null, mapOf(key to value), entity, entity.entitySource)
          }
        }
        else {
          diff.modifyEntity(ModifiableModuleCustomImlDataEntity::class.java, customImlData) {
            if (value != null) {
              customModuleOptions[key] = value
            }
            else {
              customModuleOptions.remove(key)
            }
          }
        }
      }
    }

    val diff = diff
    if (diff != null) {
      val entity = entityStorage.current.findModuleEntity(this)
      if (entity != null) {
        updateOptionInEntity(diff, entity)
      }
    }
    else {
      if (getOptionValue(key) != value) {
        WriteAction.runAndWait<RuntimeException> {
          WorkspaceModel.getInstance(project).updateProjectModel { builder ->
            val entity = builder.findModuleEntity(this)
            if (entity != null) {
              updateOptionInEntity(builder, entity)
            }
          }
        }
      }
    }
    return

  }

  override fun getOptionsModificationCount(): Long = 0
}