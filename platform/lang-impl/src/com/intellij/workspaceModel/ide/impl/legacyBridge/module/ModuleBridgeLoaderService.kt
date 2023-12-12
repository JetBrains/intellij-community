// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectServiceContainerInitializedListener
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMillis
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
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
import java.util.concurrent.atomic.AtomicLong

private val LOG: Logger
  get() = logger<ModuleBridgeLoaderService>()

private val moduleLoadingTimeMs = AtomicLong().also { setupOpenTelemetryReporting(jpsMetrics.meter) }

private fun setupOpenTelemetryReporting(meter: Meter) {
  val modulesLoadingTimeCounter = meter.counterBuilder("workspaceModel.moduleBridgeLoader.loading.modules.ms").buildObserver()

  meter.batchCallback(
    {
      modulesLoadingTimeCounter.record(moduleLoadingTimeMs.get())
    },
    modulesLoadingTimeCounter
  )
}

private class ModuleBridgeLoaderService : ProjectServiceContainerInitializedListener {
  override suspend fun execute(project: Project) {
    coroutineScope {
      val projectModelSynchronizer = project.serviceAsync<JpsProjectModelSynchronizer>()
      val workspaceModel = project.serviceAsync<WorkspaceModel>() as WorkspaceModelImpl

      launch { project.serviceAsync<ProjectRootManager>() }

      val start = System.currentTimeMillis()

      if (workspaceModel.loadedFromCache) {
        span("modules loading with cache") {
          if (projectModelSynchronizer.hasNoSerializedJpsModules()) {
            LOG.warn("Loaded from cache, but no serialized modules found. " +
                     "Workspace model cache will be ignored, project structure will be recreated.")
            workspaceModel.ignoreCache() // sets `WorkspaceModelImpl#loadedFromCache` to `false`
            project.putUserData(PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES, true)
          }
          loadModules(project = project,
                      targetBuilder = null,
                      targetUnloadedEntitiesBuilder = null,
                      loadedFromCache = workspaceModel.loadedFromCache)
        }
        val globalWorkspaceModel = serviceAsync<GlobalWorkspaceModel>()
        writeAction {
          globalWorkspaceModel.applyStateToProject(project)
        }
      }
      else {
        LOG.info("Workspace model loaded without cache. Loading real project state into workspace model. ${Thread.currentThread()}")
        val projectEntities = span("modules loading without cache") {
          val projectEntities = projectModelSynchronizer.loadProjectToEmptyStorage(project)
          loadModules(project = project,
                      targetBuilder = projectEntities?.builder,
                      targetUnloadedEntitiesBuilder = projectEntities?.unloadedEntitiesBuilder,
                      loadedFromCache = workspaceModel.loadedFromCache)
          projectEntities
        }
        if (projectEntities?.builder != null) {
          WorkspaceModelTopics.getInstance(project).notifyModulesAreLoaded()
        }
        projectModelSynchronizer.applyLoadedStorage(projectEntities)
        project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
      }

      span("tracked libraries setup") {
        val projectRootManager = coroutineScope {
          // required for setupTrackedLibrariesAndJdks - make sure that it is created to avoid blocking of EDT
          launch { serviceAsync<ProjectJdkTable>() }
          project.serviceAsync<ProjectRootManager>() as ProjectRootManagerBridge
        }
        writeAction {
          projectRootManager.setupTrackedLibrariesAndJdks()
        }
      }
      span("workspace file index initialization") {
        (project.serviceAsync<WorkspaceFileIndex>() as WorkspaceFileIndexEx).initialize()
      }

      moduleLoadingTimeMs.addElapsedTimeMillis(start)
    }
    WorkspaceModelTopics.getInstance(project).notifyModulesAreLoaded()
  }
}

private suspend fun loadModules(project: Project,
                                targetBuilder: MutableEntityStorage?,
                                targetUnloadedEntitiesBuilder: MutableEntityStorage?,
                                loadedFromCache: Boolean) {
  span("modules instantiation") {
    val moduleManager = project.serviceAsync<ModuleManager>() as ModuleManagerComponentBridge
    if (targetBuilder != null && targetUnloadedEntitiesBuilder != null) {
      moduleManager.unloadNewlyAddedModulesIfPossible(targetBuilder, targetUnloadedEntitiesBuilder)
    }
    val entities = (targetBuilder ?: moduleManager.entityStore.current).entities(ModuleEntity::class.java).toList()
    val unloadedEntities = (targetUnloadedEntitiesBuilder ?: WorkspaceModel.getInstance(project).currentSnapshotOfUnloadedEntities)
      .entities(ModuleEntity::class.java)
      .toList()
    moduleManager.loadModules(loadedEntities = entities,
                              unloadedEntities = unloadedEntities,
                              targetBuilder = targetBuilder,
                              initializeFacets = loadedFromCache)
    //childActivity?.setDescription("modules count: ${moduleManager.modules.size}")
  }

  span("libraries instantiation") {
    (LibraryTablesRegistrar.getInstance().getLibraryTable(project) as ProjectLibraryTableBridgeImpl).loadLibraries(targetBuilder)
  }
}
