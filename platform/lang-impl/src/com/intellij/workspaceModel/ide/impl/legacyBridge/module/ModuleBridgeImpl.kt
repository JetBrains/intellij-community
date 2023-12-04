// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMillis
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.jps.entities.ModuleCustomImlDataEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyEntity
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectEntitiesLoader.isModulePropertiesBridgeEnabled
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageOnSnapshot
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.workspaceModel.ide.impl.VirtualFileUrlBridge
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.TestModulePropertiesBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.toPath
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong

@Suppress("OVERRIDE_DEPRECATION")
internal class ModuleBridgeImpl(
  override var moduleEntityId: ModuleId,
  name: String,
  project: Project,
  virtualFileUrl: VirtualFileUrl?,
  override var entityStorage: VersionedEntityStorage,
  override var diff: MutableEntityStorage?
) : ModuleImpl(name = name, project = project, virtualFilePointer = virtualFileUrl as? VirtualFileUrlBridge), ModuleBridge, WorkspaceModelChangeListener {
  init {
    // default project doesn't have modules
    if (!project.isDefault && !project.isDisposed) {
      project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, this)
    }

    // This is a temporary solution and should be removed after full migration to [TestModulePropertiesBridge]
    val plugins = PluginManagerCore.getPluginSet().getEnabledModules()
    val corePluginDescriptor = plugins.find { it.pluginId == PluginManagerCore.CORE_ID }
                               ?: error("Core plugin with id: ${PluginManagerCore.CORE_ID} should be available")
    if (isModulePropertiesBridgeEnabled) {
      registerService(TestModuleProperties::class.java, TestModulePropertiesBridge::class.java, corePluginDescriptor, false)
    }
    else {
      val classLoader = javaClass.classLoader
      val implClass = classLoader.loadClass("com.intellij.openapi.roots.impl.TestModulePropertiesImpl")
      registerService(TestModuleProperties::class.java, implClass, corePluginDescriptor, false)
    }
  }

  override fun beforeChanged(event: VersionedStorageChange) = moduleBridgeBeforeChangedTimeMs.addMeasuredTimeMillis {
    event.getChanges(ModuleEntity::class.java).filterIsInstance<EntityChange.Removed<ModuleEntity>>().forEach {
      if (it.entity.symbolicId != moduleEntityId) return@forEach

      if (event.storageBefore.moduleMap.getDataByEntity(it.entity) != this@ModuleBridgeImpl) return@forEach

      val currentStore = entityStorage.current
      entityStorage = VersionedEntityStorageOnSnapshot(currentStore.toSnapshot())
      assert(moduleEntityId in entityStorage.current) {
        // If we ever get this assertion, replace use `event.storeBefore` instead of current
        // As it made in ArtifactBridge
        "Cannot resolve module $moduleEntityId. Current store: $currentStore"
      }
    }
  }

  override fun rename(newName: String, newModuleFileUrl: VirtualFileUrl?, notifyStorage: Boolean) {
    imlFilePointer = newModuleFileUrl as VirtualFileUrlBridge
    rename(newName, notifyStorage)
  }

  override fun rename(newName: String, notifyStorage: Boolean) {
    moduleEntityId = moduleEntityId.copy(name = newName)
    super<ModuleImpl>.rename(newName, notifyStorage)
  }

  override fun onImlFileMoved(newModuleFileUrl: VirtualFileUrl) {
    imlFilePointer = newModuleFileUrl as VirtualFileUrlBridge
    val imlPath = newModuleFileUrl.toPath()
    (store.storageManager as RenameableStateStorageManager).pathRenamed(imlPath, null)
    store.setPath(imlPath)
    (PathMacroManager.getInstance(this) as? ModulePathMacroManager)?.onImlFileMoved()
  }

  override fun registerComponents(modules: List<IdeaPluginDescriptorImpl>,
                                  app: Application?,
                                  precomputedExtensionModel: PrecomputedExtensionModel?,
                                  listenerCallbacks: MutableList<in Runnable>?) {
    registerComponents(corePlugin = modules.find { it.pluginId == PluginManagerCore.CORE_ID },
                       modules = modules,
                       precomputedExtensionModel = precomputedExtensionModel,
                       app = app,
                       listenerCallbacks = listenerCallbacks)
  }

  override fun callCreateComponents() {
    @Suppress("DEPRECATION")
    createComponents()
  }

  override suspend fun callCreateComponentsNonBlocking() {
    createComponentsNonBlocking()
  }

  override fun initFacets() = facetsInitializationTimeMs.addMeasuredTimeMillis {
    FacetManager.getInstance(this).allFacets.forEach(Facet<*>::initFacet)
  }

  override fun registerComponents(corePlugin: IdeaPluginDescriptor?,
                                  modules: List<IdeaPluginDescriptorImpl>,
                                  precomputedExtensionModel: PrecomputedExtensionModel?,
                                  app: Application?,
                                  listenerCallbacks: MutableList<in Runnable>?) {
    super.registerComponents(modules = modules,
                             app = app,
                             precomputedExtensionModel = precomputedExtensionModel,
                             listenerCallbacks = listenerCallbacks)
    if (corePlugin == null) {
      return
    }
    unregisterComponent(DeprecatedModuleOptionManager::class.java)
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
            diff addEntity ModuleCustomImlDataEntity(HashMap(mapOf(key to value)), entity.entitySource) {
              module = entity
            }
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

    val start = System.currentTimeMillis()

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

    updateOptionTimeMs.addElapsedTimeMillis(start)
    return
  }

  companion object {
    private val moduleBridgeBeforeChangedTimeMs: AtomicLong = AtomicLong()
    private val facetsInitializationTimeMs: AtomicLong = AtomicLong()
    private val updateOptionTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val moduleBridgeBeforeChangedTimeCounter = meter.counterBuilder("workspaceModel.moduleBridge.before.changed.ms").buildObserver()
      val facetsInitializationTimeCounter = meter.counterBuilder("workspaceModel.moduleBridge.facet.initialization.ms").buildObserver()
      val updateOptionTimeCounter = meter.counterBuilder("workspaceModel.moduleBridge.update.option.ms").buildObserver()

      meter.batchCallback(
        {
          moduleBridgeBeforeChangedTimeCounter.record(moduleBridgeBeforeChangedTimeMs.get())
          facetsInitializationTimeCounter.record(facetsInitializationTimeMs.get())
          updateOptionTimeCounter.record(updateOptionTimeMs.get())
        },
        moduleBridgeBeforeChangedTimeCounter, facetsInitializationTimeCounter, updateOptionTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}