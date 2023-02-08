// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectServiceContainerInitializedListener
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class ModuleBridgeLoaderService : ProjectServiceContainerInitializedListener {
  companion object {
    private val LOG = logger<ModuleBridgeLoaderService>()
  }

  private suspend fun loadModules(project: Project, activity: Activity?, targetBuilder: MutableEntityStorage?, loadedFromCache: Boolean) {
    val componentManager = project as ComponentManagerEx

    val childActivity = activity?.startChild("modules instantiation")
    // ModuleManagerComponentBridge calls WorkspaceModel in init - getting entityStorage
    componentManager.getServiceAsync(WorkspaceModel::class.java).await()

    val moduleManager = componentManager.getServiceAsync(ModuleManager::class.java).await() as ModuleManagerComponentBridge
    if (targetBuilder != null) {
      moduleManager.unloadNewlyAddedModulesIfPossible(targetBuilder)
    }
    val entities = (targetBuilder ?: moduleManager.entityStore.current).entities(ModuleEntity::class.java)
    moduleManager.loadModules(entities, targetBuilder, loadedFromCache)
    childActivity?.setDescription("modules count: ${moduleManager.modules.size}")
    childActivity?.end()

    runActivity("libraries instantiation") {
      (LibraryTablesRegistrar.getInstance().getLibraryTable(project) as ProjectLibraryTableBridgeImpl).loadLibraries(targetBuilder)
    }
    activity?.end()
  }

  override suspend fun execute(project: Project) {
    val projectModelSynchronizer = JpsProjectModelSynchronizer.getInstance(project)
    val componentManagerEx = project as ComponentManagerEx
    val workspaceModel = componentManagerEx.getServiceAsync(WorkspaceModel::class.java).await() as WorkspaceModelImpl
    if (workspaceModel.loadedFromCache) {
      val activity = StartUpMeasurer.startActivity("modules loading with cache")
      if (projectModelSynchronizer.hasNoSerializedJpsModules()) {
        LOG.warn("Loaded from cache, but no serialized modules found. " +
                 "Workspace model cache will be ignored, project structure will be recreated.")
        workspaceModel.ignoreCache() // sets `WorkspaceModelImpl#loadedFromCache` to `false`
        project.putUserData(PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES, true)
      }
      loadModules(project, activity, null, workspaceModel.loadedFromCache)
    }
    else {
      LOG.info("Workspace model loaded without cache. Loading real project state into workspace model. ${Thread.currentThread()}")
      val activity = StartUpMeasurer.startActivity("modules loading without cache")
      val storeToEntitySources = projectModelSynchronizer.loadProjectToEmptyStorage(project)


      loadModules(project, activity, storeToEntitySources?.first, workspaceModel.loadedFromCache)
      if (storeToEntitySources?.first != null) {
        WorkspaceModelTopics.getInstance(project).notifyModulesAreLoaded()
      }
      projectModelSynchronizer.applyLoadedStorage(storeToEntitySources)
      project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
    }

    runActivity("tracked libraries setup") {
      // required for setupTrackedLibrariesAndJdks - make sure that it is created to avoid blocking of EDT
      val jdkTableDeferred = (ApplicationManager.getApplication() as ComponentManagerEx).getServiceAsync(ProjectJdkTable::class.java)
      val projectRootManager = componentManagerEx.getServiceAsync(ProjectRootManager::class.java).await() as ProjectRootManagerBridge
      jdkTableDeferred.join()
      withContext(Dispatchers.EDT) {
        ApplicationManager.getApplication().runWriteAction {
          projectRootManager.setupTrackedLibrariesAndJdks()
        }
      }
    }
    if (WorkspaceFileIndexEx.IS_ENABLED) {
      runActivity("workspace file index initialization") {
        readAction {
          (WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexEx).ensureInitialized()
        }
      }
    }
    WorkspaceModelTopics.getInstance(project).notifyModulesAreLoaded()
  }
}