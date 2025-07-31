// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.configurationStore.DefaultModuleStoreFactory
import com.intellij.configurationStore.ModuleStoreFactory
import com.intellij.configurationStore.NonPersistentModuleStore
import com.intellij.configurationStore.RenameableStateStorageManager
import com.intellij.facet.FacetManagerFactory
import com.intellij.facet.impl.FacetEventsPublisher
import com.intellij.facet.impl.FacetManagerFactoryImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.components.impl.stores.ComponentStoreOwner
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.Milliseconds
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.diagnostic.telemetry.impl.span
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
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

@Suppress("OVERRIDE_DEPRECATION")
@ApiStatus.Internal
open class ModuleBridgeImpl(
  override var moduleEntityId: ModuleId,
  name: String,
  project: Project,
  virtualFileUrl: VirtualFileUrlBridge?,
  override var entityStorage: VersionedEntityStorage,
  override var diff: MutableEntityStorage?,
  componentManager: ComponentManager,
) : ModuleImpl(name = name, project = project, componentManager = componentManager), ModuleBridge, ComponentStoreOwner {
  private var imlFilePointer: VirtualFilePointer? = virtualFileUrl

  override fun getModuleFile(): VirtualFile? = imlFilePointer?.file

  override fun canStoreSettings(): Boolean = imlFilePointer != null && componentStore !is NonPersistentModuleStore

  override fun rename(newName: String, newModuleFileUrl: VirtualFileUrl?, notifyStorage: Boolean) {
    imlFilePointer = newModuleFileUrl as VirtualFileUrlBridge
    rename(newName, notifyStorage)
  }

  override fun getModuleNioFile(): Path {
    // FIXME (IJPL-188482): we have a race: saving a project collect save sessions, which have reference to PathMacroManager.
    //  PathMacroManager might be disposed (together with the module) by the moment when save sessions are actually committed to the disk.
    //  Reproducer: com.intellij.workspaceModel.integrationTests.tests.aggregator.maven.changes.MavenMultiModulesProjectAddTwoModulesTest.mavenMultiModulesProjectAddTwoModules
    if (imlFilePointer != null) {
      @Suppress("DEPRECATION")
      val isDisposed = Disposer.isDisposed(this)
      if (!isDisposed) {
        val store = componentStore
        if (store !is NonPersistentModuleStore) {
          return store.storageManager.expandMacro(StoragePathMacros.MODULE_FILE)
        }
      }
    }
    return Path.of("")
  }

  override fun rename(newName: String, notifyStorage: Boolean) {
    moduleEntityId = moduleEntityId.copy(name = newName)
    super<ModuleImpl>.rename(newName, notifyStorage)
  }

  override fun onImlFileMoved(newModuleFileUrl: VirtualFileUrl) {
    // There are some cases when `ModuleBridgeImpl` starts saving data into the IML (e.g., new Gradle import), so we
    // need to reregister `IComponentStore` from `NonPersistentModuleStore` to `ModuleStoreImpl`
    val shouldResetModuleStore = (imlFilePointer == null)
    imlFilePointer = newModuleFileUrl as VirtualFileUrlBridge
    if (shouldResetModuleStore) {
      resetModuleStore()
    }
    val imlPath = newModuleFileUrl.toPath()
    val componentStore = componentStore
    (componentStore.storageManager as? RenameableStateStorageManager)?.pathRenamed(imlPath, null)
    componentStore.setPath(imlPath)
    (PathMacroManager.getInstance(this) as? ModulePathMacroManager)?.onImlFileMoved()
  }

  override fun markContainerAsCreated() {
    @Suppress("DEPRECATION")
    getModuleComponentManager().markContainerAsCreated()
  }

  override fun initServiceContainer(precomputedExtensionModel: PrecomputedExtensionModel) {
    getModuleComponentManager().initModuleContainer(precomputedExtensionModel)
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

    internal suspend fun initFacets(modules: Collection<Pair<ModuleEntity, ModuleBridge>>, project: Project) {
      val facetManagerFactory = project.serviceAsync<FacetManagerFactory>() as FacetManagerFactoryImpl
      span("init facets in EDT", Dispatchers.UiWithModelAccess) {
        facetsInitializationTimeMs.addMeasuredTime {
          doInitFacetsInEdt(modules, facetManagerFactory)
        }
      }
      span("send onFacetAdded events") {
        project.serviceAsync<FacetEventsPublisher>().sendEvents(facetManagerFactory)
      }
    }

    // separate method to see it in a profiler
    private fun doInitFacetsInEdt(
      modules: Collection<Pair<ModuleEntity, ModuleBridge>>,
      facetManagerFactory: FacetManagerFactory,
    ) {
      for ((_, module) in modules) {
        if (!module.isDisposed) {
          facetsInitializationTimeMs.addMeasuredTime {
            for (facet in facetManagerFactory.getFacetManager(module).allFacets) {
              facet.initFacet()
            }
          }
        }
      }
    }

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

  internal fun resetModuleStore() {
    val existingStore = componentStoreRef.getAndSet(null)
    existingStore?.release()
  }

  private val componentStoreRef = AtomicReference<IComponentStore?>()

  override val componentStore: IComponentStore
    get() {
      while (true) {
        val existing = componentStoreRef.get()
        if (existing != null) {
          return existing
        }

        val newInstance = if (imlFilePointer == null) {
          DefaultModuleStoreFactory.createNonPersistentStore()
        }
        else {
          ApplicationManager.getApplication().service<ModuleStoreFactory>().createModuleStore(this)
        }
        if (componentStoreRef.compareAndSet(null, newInstance)) {
          return newInstance
        }
        else {
          newInstance.release()
        }
      }
    }
}