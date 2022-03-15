// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectServiceContainerInitializedListener
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

internal class ModuleBridgeLoaderService(private val project: Project) {
  private var storeToEntitySources: Pair<WorkspaceEntityStorage, List<EntitySource>>? = null
  private var activity: Activity? = null

  init {
    if (!project.isDefault) {
      val workspaceModel = WorkspaceModel.getInstance(project) as WorkspaceModelImpl
      val projectModelSynchronizer = JpsProjectModelSynchronizer.getInstance(project)
      if (projectModelSynchronizer != null) {
        if (workspaceModel.loadedFromCache && projectModelSynchronizer.hasNoSerializedJpsModules()) {
          LOG.warn("Loaded from cache, but no serialized modules found. Workspace model cache will be ignored, project structure will be recreated.")
          workspaceModel.ignoreCache() // sets `WorkspaceModelImpl#loadedFromCache` to `false`
          project.putUserData(PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES, true)
        }
        if (!workspaceModel.loadedFromCache) {
          LOG.info("Workspace model loaded without cache. Loading real project state into workspace model. ${Thread.currentThread()}")
          activity = StartUpMeasurer.startActivity("modules loading without cache", ActivityCategory.DEFAULT)
          storeToEntitySources = projectModelSynchronizer.loadProjectToEmptyStorage(project)
        }
        else {
          activity = StartUpMeasurer.startActivity("modules loading with cache", ActivityCategory.DEFAULT)
          loadModules()
        }
      }
    }
  }

  private fun loadModules() {
    val childActivity = activity?.startChild("modules instantiation")
    val moduleManager = ModuleManager.getInstance(project) as ModuleManagerComponentBridge
    val entities = moduleManager.entityStore.current.entities(ModuleEntity::class.java)
    moduleManager.loadModules(entities)
    childActivity?.setDescription("modules count: ${moduleManager.modules.size}")
    childActivity?.end()
    val librariesActivity = StartUpMeasurer.startActivity("libraries instantiation", ActivityCategory.DEFAULT)
    (LibraryTablesRegistrar.getInstance().getLibraryTable(project) as ProjectLibraryTableBridgeImpl).loadLibraries()
    librariesActivity.end()
    activity?.end()
    activity = null
  }

  class ModuleBridgeProjectServiceInitializedListener : ProjectServiceContainerInitializedListener {
    override fun serviceCreated(project: Project) {
      LOG.debug { "Project component initialized" }
      if (project.isDefault) return
      val workspaceModel = WorkspaceModel.getInstance(project) as WorkspaceModelImpl
      if (!workspaceModel.loadedFromCache) {
        val moduleLoaderService = project.getService(ModuleBridgeLoaderService::class.java)
        val projectModelSynchronizer = JpsProjectModelSynchronizer.getInstance(project)
        if (projectModelSynchronizer == null) return
        projectModelSynchronizer.applyLoadedStorage(moduleLoaderService.storeToEntitySources)
        project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
        moduleLoaderService.storeToEntitySources = null
        moduleLoaderService.loadModules()
      }
      WriteAction.runAndWait<RuntimeException> {
        (ProjectRootManager.getInstance(project) as ProjectRootManagerBridge).setupTrackedLibrariesAndJdks()
      }
      WorkspaceModelTopics.getInstance(project).notifyModulesAreLoaded()
    }

    companion object {
      private val LOG = logger<ModuleBridgeProjectServiceInitializedListener>()
    }
  }

  companion object {
    private val LOG = logger<ModuleBridgeLoaderService>()
  }
}