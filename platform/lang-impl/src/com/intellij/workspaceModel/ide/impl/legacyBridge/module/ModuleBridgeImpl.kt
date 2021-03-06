// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.facet.FacetFromExternalSourcesStorage
import com.intellij.facet.FacetManager
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.VirtualFileUrlBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetManagerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge.Companion.findModuleEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnStorage
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.picocontainer.MutablePicoContainer

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
    if (!project.isDefault) {
      val busConnection = project.messageBus.connect(this)

      WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, object : WorkspaceModelChangeListener {
        override fun beforeChanged(event: VersionedStorageChange) {
          event.getChanges(ModuleEntity::class.java).filterIsInstance<EntityChange.Removed<ModuleEntity>>().forEach {
            if (it.entity.persistentId() == moduleEntityId) {
              val currentStore = entityStorage.current
              val storage = if (currentStore is WorkspaceEntityStorageBuilder) currentStore.toStorage() else currentStore
              entityStorage = VersionedEntityStorageOnStorage(storage)
              assert(entityStorage.current.resolve(
                moduleEntityId) != null) { "Cannot resolve module $moduleEntityId. Current store: $currentStore" }
            }
          }
        }
      })
    }
  }

  fun rename(newName: String, newModuleFileUrl: VirtualFileUrl?, notifyStorage: Boolean) {
    myImlFilePointer = newModuleFileUrl as VirtualFileUrlBridge
    rename(newName, notifyStorage)
  }

  override fun rename(newName: String, notifyStorage: Boolean) {
    moduleEntityId = moduleEntityId.copy(name = newName)
    super<ModuleImpl>.rename(newName, notifyStorage)
  }

  override fun registerComponents(plugins: List<IdeaPluginDescriptorImpl>, listenerCallbacks: MutableList<Runnable>?) {
    super.registerComponents(plugins, null)

    val corePlugin = plugins.find { it.pluginId == PluginManagerCore.CORE_ID }
    if (corePlugin != null) {
      registerComponent(ModuleRootManager::class.java, ModuleRootComponentBridge::class.java, corePlugin, true)
      registerComponent(FacetManager::class.java, FacetManagerBridge::class.java, corePlugin, true)
      (picoContainer as MutablePicoContainer).unregisterComponent(DeprecatedModuleOptionManager::class.java)

      try { //todo improve
        val apiClass = Class.forName("com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager", true, javaClass.classLoader)
        val implClass = Class.forName("com.intellij.openapi.externalSystem.service.project.ExternalSystemModulePropertyManagerBridge", true,
                                      javaClass.classLoader)
        registerService(apiClass, implClass, corePlugin, true)
      }
      catch (ignored: Throwable) {
      }
      (picoContainer as MutablePicoContainer).unregisterComponent(FacetFromExternalSourcesStorage::class.java.name)
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
        diff.modifyEntity(ModifiableModuleEntity::class.java, entity, { type = value })
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
      WriteAction.runAndWait<RuntimeException> {
        WorkspaceModel.getInstance(project).updateProjectModel { builder ->
          val entity = builder.findModuleEntity(this)
          if (entity != null) {
            updateOptionInEntity(builder, entity)
          }
        }
      }
    }
    return

  }

  override fun getOptionsModificationCount(): Long = 0
}