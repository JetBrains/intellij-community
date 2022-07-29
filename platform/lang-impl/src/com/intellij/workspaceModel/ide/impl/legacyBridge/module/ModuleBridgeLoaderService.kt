// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
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
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class ModuleBridgeLoaderService : ProjectServiceContainerInitializedListener {
  companion object {
    private val LOG = logger<ModuleBridgeLoaderService>()
  }

  private suspend fun loadModules(project: Project, activity: Activity?) {
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
  }

  override suspend fun serviceCreated(project: Project) {
    val projectModelSynchronizer = JpsProjectModelSynchronizer.getInstance(project)
    val workspaceModel = WorkspaceModel.getInstance(project) as WorkspaceModelImpl
    if (workspaceModel.loadedFromCache) {
      if (JpsProjectModelSynchronizer.getInstance(project).hasNoSerializedJpsModules()) {
        LOG.warn("Loaded from cache, but no serialized modules found. " +
                 "Workspace model cache will be ignored, project structure will be recreated.")
        workspaceModel.ignoreCache() // sets `WorkspaceModelImpl#loadedFromCache` to `false`
        project.putUserData(PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES, true)
      }

      val activity = StartUpMeasurer.startActivity("modules loading with cache")
      loadModules(project, activity)
    }
    else {
      LOG.info("Workspace model loaded without cache. Loading real project state into workspace model. ${Thread.currentThread()}")
      val activity = StartUpMeasurer.startActivity("modules loading without cache")
      val storeToEntitySources = projectModelSynchronizer.loadProjectToEmptyStorage(project)

      projectModelSynchronizer.applyLoadedStorage(storeToEntitySources)
      project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
      loadModules(project, activity)
    }

    runActivity("tracked libraries setup") {
      val projectRootManager = ProjectRootManager.getInstance(project) as ProjectRootManagerBridge
      withContext(Dispatchers.EDT) {
        ApplicationManager.getApplication().runWriteAction {
          projectRootManager.setupTrackedLibrariesAndJdks()
        }
      }
    }
    WorkspaceModelTopics.getInstance(project).notifyModulesAreLoaded()
  }
}