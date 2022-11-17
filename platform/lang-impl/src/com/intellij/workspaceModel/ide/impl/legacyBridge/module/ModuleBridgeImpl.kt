// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.configurationStore.RenameableStateStorageManager
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
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
import com.intellij.openapi.roots.TestModuleProperties
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.VirtualFileUrlBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.TestModulePropertiesBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.toPath
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.bridgeEntities.addModuleCustomImlDataEntity
import com.intellij.workspaceModel.storage.bridgeEntities.modifyEntity
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnStorage
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

@Suppress("OVERRIDE_DEPRECATION")
internal class ModuleBridgeImpl(
  override var moduleEntityId: ModuleId,
  name: String,
  project: Project,
  virtualFileUrl: VirtualFileUrl?,
  override var entityStorage: VersionedEntityStorage,
  override var diff: MutableEntityStorage?
) : ModuleImpl(name, project, virtualFileUrl as? VirtualFileUrlBridge), ModuleBridge {
  init {
    // default project doesn't have modules
    if (!project.isDefault && !project.isDisposed) {
      project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
        override fun beforeChanged(event: VersionedStorageChange) {
          event.getChanges(ModuleEntity::class.java).filterIsInstance<EntityChange.Removed<ModuleEntity>>().forEach {
            if (it.entity.symbolicId != moduleEntityId) return@forEach

            if (event.storageBefore.moduleMap.getDataByEntity(it.entity) != this@ModuleBridgeImpl) return@forEach

            val currentStore = entityStorage.current
            val storage = if (currentStore is MutableEntityStorage) currentStore.toSnapshot() else currentStore
            entityStorage = VersionedEntityStorageOnStorage(storage)
            assert(moduleEntityId in entityStorage.current) {
              // If we ever get this assertion, replace use `event.storeBefore` instead of current
              // As it made in ArtifactBridge
              "Cannot resolve module $moduleEntityId. Current store: $currentStore"
            }
          }
        }
      })
    }

    // This is temporary solution and should be removed after full migration to [TestModulePropertiesBridge]
    val plugins = PluginManagerCore.getPluginSet().getEnabledModules()
    val corePluginDescriptor = plugins.find { it.pluginId == PluginManagerCore.CORE_ID }
                               ?: error("Core plugin with id: ${PluginManagerCore.CORE_ID} should be available")
    if (TestModuleProperties.testModulePropertiesBridgeEnabled()) {
      registerService(TestModuleProperties::class.java, TestModulePropertiesBridge::class.java, corePluginDescriptor, false)
    } else {
      val classLoader = javaClass.classLoader
      val implClass = classLoader.loadClass("com.intellij.openapi.roots.impl.TestModulePropertiesImpl")
      registerService(TestModuleProperties::class.java, implClass, corePluginDescriptor, false)
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

  override fun registerComponents(modules: List<IdeaPluginDescriptorImpl>,
                                  app: Application?,
                                  precomputedExtensionModel: PrecomputedExtensionModel?,
                                  listenerCallbacks: MutableList<in Runnable>?) {
    registerComponents(modules.find { it.pluginId == PluginManagerCore.CORE_ID }, modules, precomputedExtensionModel, app, listenerCallbacks)
  }

  override fun callCreateComponents() {
    @Suppress("DEPRECATION")
    createComponents()
  }

  override suspend fun callCreateComponentsNonBlocking() {
    createComponentsNonBlocking()
  }

  override fun initFacets() {
    FacetManager.getInstance(this).allFacets.forEach(Facet<*>::initFacet)
  }

  override fun registerComponents(corePlugin: IdeaPluginDescriptor?,
                                  modules: List<IdeaPluginDescriptorImpl>,
                                  precomputedExtensionModel: PrecomputedExtensionModel?,
                                  app: Application?,
                                  listenerCallbacks: MutableList<in Runnable>?) {
    super.registerComponents(modules, app, precomputedExtensionModel, listenerCallbacks)
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
    val moduleEntity = this.findModuleEntity(entityStorage.current)
    if (key == Module.ELEMENT_TYPE) {
      return moduleEntity?.type
    }
    return moduleEntity?.customImlData?.customModuleOptions?.get(key)
  }

  override fun setOption(key: String, value: String?) {
    fun updateOptionInEntity(diff: MutableEntityStorage, entity: ModuleEntity) {
      if (key == Module.ELEMENT_TYPE) {
        diff.modifyEntity(entity) { type = value }
      }
      else {
        val customImlData = entity.customImlData
        if (customImlData == null) {
          if (value != null) {
            diff.addModuleCustomImlDataEntity(null, mapOf(key to value), entity, entity.entitySource)
          }
        }
        else {
          diff.modifyEntity(customImlData) {
            if (value != null) {
              customModuleOptions = customModuleOptions.toMutableMap().also { it[key] = value }
            }
            else {
              customModuleOptions = customModuleOptions.toMutableMap().also { it.remove(key) }
            }
          }
        }
      }
    }

    val diff = diff
    if (diff != null) {
      val entity = this.findModuleEntity(entityStorage.current)
      if (entity != null) {
        updateOptionInEntity(diff, entity)
      }
    }
    else {
      @Suppress("DEPRECATION")
      if (getOptionValue(key) != value) {
        WriteAction.runAndWait<RuntimeException> {
          WorkspaceModel.getInstance(project).updateProjectModel("Set option in module entity") { builder ->
            val entity = this.findModuleEntity(builder)
            if (entity != null) {
              updateOptionInEntity(builder, entity)
            }
          }
        }
      }
    }
    return

  }
}