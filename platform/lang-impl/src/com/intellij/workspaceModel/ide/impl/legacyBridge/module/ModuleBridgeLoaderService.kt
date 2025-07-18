// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.platform.PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.diagnostic.telemetry.helpers.Milliseconds
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.JpsGlobalModelSynchronizer
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val LOG: Logger
  get() = logger<ModuleBridgeLoaderService>()

private val moduleLoadingTimeMs = MillisecondsMeasurer().also { setupOpenTelemetryReporting(jpsMetrics.meter) }

private fun setupOpenTelemetryReporting(meter: Meter) {
  val modulesLoadingTimeCounter = meter.counterBuilder("workspaceModel.moduleBridgeLoader.loading.modules.ms").buildObserver()

  meter.batchCallback(
    {
      modulesLoadingTimeCounter.record(moduleLoadingTimeMs.asMilliseconds())
    },
    modulesLoadingTimeCounter
  )
}

private class ModuleBridgeLoaderService : InitProjectActivity {
  override suspend fun run(project: Project) {
    coroutineScope {
      val projectModelSynchronizer = project.serviceAsync<JpsProjectModelSynchronizer>()
      val workspaceModel = project.serviceAsync<WorkspaceModel>() as WorkspaceModelImpl

      launch {
        readAction {
          ProjectRootManager.getInstance(project)
        }
      }

      val start = Milliseconds.now()

      if (workspaceModel.loadedFromCache) {
        span("modules loading with cache") {
          if (projectModelSynchronizer.hasNoSerializedJpsModules()) {
            LOG.warn("Loaded from cache, but no serialized modules found. " +
                     "Workspace model cache will be ignored, project structure will be recreated.")
            workspaceModel.ignoreCache() // sets `WorkspaceModelImpl#loadedFromCache` to `false`
            project.putUserData(PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES, true)
          }
          loadModules(
            project = project,
            targetBuilder = null,
            targetUnloadedEntitiesBuilder = null,
            loadedFromCache = workspaceModel.loadedFromCache,
            workspaceModel = workspaceModel,
          )
        }
        val globalWorkspaceModel = GlobalWorkspaceModel.getInstanceAsync(project.getEelDescriptor().machine)
        backgroundWriteAction {
          globalWorkspaceModel.applyStateToProject(project)
        }
      }
      else {
        LOG.info("Workspace model loaded without cache. Loading real project state into workspace model. ${Thread.currentThread()}")
        val projectSyncJob = launch {
          val projectEntities = span("modules loading without cache") {
            val projectEntities = projectModelSynchronizer.loadProjectToEmptyStorage(project, workspaceModel)
            loadModules(
              project = project,
              targetBuilder = projectEntities?.builder,
              targetUnloadedEntitiesBuilder = projectEntities?.unloadedEntitiesBuilder,
              loadedFromCache = workspaceModel.loadedFromCache,
              workspaceModel = workspaceModel,
            )
            projectEntities
          }
          if (projectEntities?.builder != null) {
            @Suppress("DEPRECATION")
            project.serviceAsync<WorkspaceModelTopics>().notifyModulesAreLoaded()
          }
          projectModelSynchronizer.applyLoadedStorage(projectEntities, workspaceModel)
          project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
        }
        
        // Set the project synchronization job on the global synchronizer to prevent race conditions
        val globalWorkspaceModel = GlobalWorkspaceModel.getInstanceAsync(project.getEelDescriptor().machine)
        JpsGlobalModelSynchronizer.getInstance().setProjectSynchronizationJob(projectSyncJob)
      }

      span("tracked libraries setup") {
        val projectRootManager = coroutineScope {
          // required for setupTrackedLibrariesAndJdks - make sure that it is created to avoid blocking of EDT
          launch { serviceAsync<ProjectJdkTable>() }
          project.serviceAsync<ProjectRootManager>() as ProjectRootManagerBridge
        }
        backgroundWriteAction {
          projectRootManager.setupTrackedLibrariesAndJdks()
        }
      }

      moduleLoadingTimeMs.addElapsedTime(start)
    }

    @Suppress("DEPRECATION")
    project.serviceAsync<WorkspaceModelTopics>().notifyModulesAreLoaded()
  }
}

private suspend fun loadModules(
  project: Project,
  workspaceModel: WorkspaceModelImpl,
  targetBuilder: MutableEntityStorage?,
  targetUnloadedEntitiesBuilder: MutableEntityStorage?,
  loadedFromCache: Boolean,
) {
  span("modules instantiation") {
    val moduleManager = project.serviceAsync<ModuleManager>() as ModuleManagerComponentBridge
    if (targetBuilder != null && targetUnloadedEntitiesBuilder != null) {
      val (modulesToLoad, modulesToUnload) = moduleManager.calculateUnloadModules(targetBuilder, targetUnloadedEntitiesBuilder)
      moduleManager.updateUnloadedStorage(modulesToLoad, modulesToUnload)
    }

    val entities = (targetBuilder ?: moduleManager.entityStore.current).entities(ModuleEntity::class.java).toList()
    val unloadedEntities = (targetUnloadedEntitiesBuilder
                            ?: (project.serviceAsync<WorkspaceModel>() as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities)
      .entities(ModuleEntity::class.java)
      .toList()
    moduleManager.loadModules(loadedEntities = entities,
                              unloadedEntities = unloadedEntities,
                              targetBuilder = targetBuilder,
                              initializeFacets = loadedFromCache)
  }

  span("libraries instantiation") {
    (serviceAsync<LibraryTablesRegistrar>().getLibraryTable(project) as ProjectLibraryTableBridgeImpl)
      .loadLibraries(targetBuilder, workspaceModel)
  }
}
