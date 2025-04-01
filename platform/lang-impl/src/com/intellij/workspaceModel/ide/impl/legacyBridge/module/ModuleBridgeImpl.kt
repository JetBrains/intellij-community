// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.configurationStore.RenameableStateStorageManager
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerFactory
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleImpl
import com.intellij.openapi.module.impl.NonPersistentModuleStore
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.Milliseconds
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.workspaceModel.ide.impl.VirtualFileUrlBridge
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.toPath
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.ApiStatus

@Suppress("OVERRIDE_DEPRECATION")
@ApiStatus.Internal
class ModuleBridgeImpl(
  override var moduleEntityId: ModuleId,
  name: String,
  project: Project,
  virtualFileUrl: VirtualFileUrl?,
  override var entityStorage: VersionedEntityStorage,
  override var diff: MutableEntityStorage?,
  componentManager: ComponentManager,
) : ModuleImpl(
  name = name,
  project = project,
  virtualFilePointer = virtualFileUrl as? VirtualFileUrlBridge,
  componentManager = componentManager
), ModuleBridge {

  //override fun beforeChanged(event: VersionedStorageChange) = moduleBridgeBeforeChangedTimeMs.addMeasuredTime {
  //  val moduleEntityChanges = event.getChanges(ModuleEntity::class.java)
  //  moduleEntityChanges.forEach {
  //    if (it !is EntityChange.Removed<ModuleEntity>) return@forEach
  //    if (it.entity.symbolicId != moduleEntityId) return@forEach
  //
  //    if (event.storageBefore.moduleMap.getDataByEntity(it.entity) != this@ModuleBridgeImpl) return@forEach
  //
  //    val currentStore = entityStorage.current
  //    entityStorage = VersionedEntityStorageOnSnapshot(currentStore.toSnapshot())
  //    assert(moduleEntityId in entityStorage.current) {
  //      // If we ever get this assertion, replace use `event.storeBefore` instead of current
  //      // As it made in ArtifactBridge
  //      "Cannot resolve module $moduleEntityId. Current store: $currentStore"
  //    }
  //  }
  //}

  override fun rename(newName: String, newModuleFileUrl: VirtualFileUrl?, notifyStorage: Boolean) {
    imlFilePointer = newModuleFileUrl as VirtualFileUrlBridge
    rename(newName, notifyStorage)
  }

  override fun rename(newName: String, notifyStorage: Boolean) {
    moduleEntityId = moduleEntityId.copy(name = newName)
    super<ModuleImpl>.rename(newName, notifyStorage)
  }

  override fun onImlFileMoved(newModuleFileUrl: VirtualFileUrl) {
    // There are some cases when `ModuleBridgeImpl` starts saving data into the IML (e.g., new Gradle import), so we
    // need to reregister `IComponentStore` from `NonPersistentModuleStore` to `ModuleStoreImpl`
    if (imlFilePointer == null && store is NonPersistentModuleStore) {
      val plugins = PluginManagerCore.getPluginSet().getEnabledModules()
      val corePluginDescriptor = plugins.find { it.pluginId == PluginManagerCore.CORE_ID }
                                 ?: error("Core plugin with id: ${PluginManagerCore.CORE_ID} should be available")

      val classLoader = javaClass.classLoader
      val moduleStoreImpl = classLoader.loadClass("com.intellij.configurationStore.ModuleStoreImpl")
      getModuleComponentManager().registerService(
        serviceInterface = IComponentStore::class.java,
        implementation = moduleStoreImpl,
        pluginDescriptor = corePluginDescriptor,
        override = true
      )
    }
    imlFilePointer = newModuleFileUrl as VirtualFileUrlBridge
    val imlPath = newModuleFileUrl.toPath()
    (store.storageManager as? RenameableStateStorageManager)?.pathRenamed(imlPath, null)
    store.setPath(imlPath)
    (PathMacroManager.getInstance(this) as? ModulePathMacroManager)?.onImlFileMoved()
  }

  override fun callCreateComponents() {
    @Suppress("DEPRECATION")
    getModuleComponentManager().createComponents()
  }

  override suspend fun callCreateComponentsNonBlocking() {
    getModuleComponentManager().createComponentsNonBlocking()
    // We want to initialize FacetManager early to avoid initializing it on EDT in ModuleManagerBridgeImpl.loadModules
    project.serviceAsync<FacetManagerFactory>().getFacetManager(this)
  }

  override fun initFacets() = facetsInitializationTimeMs.addMeasuredTime {
    FacetManager.getInstance(this).allFacets.forEach(Facet<*>::initFacet)
  }

  override fun registerComponents(
    modules: List<IdeaPluginDescriptorImpl>,
    app: Application?,
    precomputedExtensionModel: PrecomputedExtensionModel?,
    listenerCallbacks: MutableList<in Runnable>?,
  ) {
    getModuleComponentManager().registerComponents(
      modules = modules,
      app = app,
      precomputedExtensionModel = precomputedExtensionModel,
      listenerCallbacks = listenerCallbacks,
    )
  }

  override fun getOptionValue(key: String): String? {
    val moduleEntity = this.findModuleEntity(entityStorage.current)
    if (key == Module.ELEMENT_TYPE) {
      return moduleEntity?.type?.name
    }
    return moduleEntity?.customImlData?.customModuleOptions?.get(key)
  }

  override fun setOption(key: String, value: String?) {
    fun updateOptionInEntity(diff: MutableEntityStorage, entity: ModuleEntity) {
      if (key == Module.ELEMENT_TYPE) {
        diff.modifyModuleEntity(entity) {
          type = if (value != null) ModuleTypeId(value) else null
        }
      }
      else {
        val customImlData = entity.customImlData
        if (customImlData == null) {
          if (value != null) {
            diff.modifyModuleEntity(entity) {
              this.customImlData = ModuleCustomImlDataEntity(HashMap(mapOf(key to value)), entity.entitySource)
            }
          }
        }
        else {
          diff.modifyModuleCustomImlDataEntity(customImlData) {
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

    val start = Milliseconds.now()

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

    updateOptionTimeMs.addElapsedTime(start)
    return
  }

  companion object {
    private val moduleBridgeBeforeChangedTimeMs = MillisecondsMeasurer()
    private val facetsInitializationTimeMs = MillisecondsMeasurer()
    private val updateOptionTimeMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val moduleBridgeBeforeChangedTimeCounter = meter.counterBuilder("workspaceModel.moduleBridge.before.changed.ms").buildObserver()
      val facetsInitializationTimeCounter = meter.counterBuilder("workspaceModel.moduleBridge.facet.initialization.ms").buildObserver()
      val updateOptionTimeCounter = meter.counterBuilder("workspaceModel.moduleBridge.update.option.ms").buildObserver()

      meter.batchCallback(
        {
          moduleBridgeBeforeChangedTimeCounter.record(moduleBridgeBeforeChangedTimeMs.asMilliseconds())
          facetsInitializationTimeCounter.record(facetsInitializationTimeMs.asMilliseconds())
          updateOptionTimeCounter.record(updateOptionTimeMs.asMilliseconds())
        },
        moduleBridgeBeforeChangedTimeCounter, facetsInitializationTimeCounter, updateOptionTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}