// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.ide.actions.cache.*
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntity
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.entities
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
class ExternalProjectsDataRecoveryAction : RecoveryAction {

  override val performanceRate: Int
    get() = 0

  override val presentableName: String
    get() = ExternalSystemBundle.message("action.ExternalSystem.RecoveryAction.name")

  override val actionKey: String
    get() = "ExternalSystem.RecoveryAction"

  override fun canBeApplied(recoveryScope: RecoveryScope): Boolean {
    return recoveryScope is ProjectRecoveryScope && recoveryScope.project.basePath?.let {
      Files.isDirectory(Paths.get(it))
    } == true
  }

  override fun perform(recoveryScope: RecoveryScope): CompletableFuture<AsyncRecoveryResult> {
    return CoroutineScopeService.coroutineScope().async {
      invalidateLocalExternalSystemCache(recoveryScope)
      invalidateProjectBuildClasspathCache(recoveryScope)
      invalidateExternalSystemDataStorage(recoveryScope)
      invalidateExternalSystemToolwindow(recoveryScope)

      val externalSystemCache = ExternalProjectsDataStorage.getProjectConfigurationDir(recoveryScope.project)

      val projectPath = closeProject(recoveryScope)

      withContext(Dispatchers.IO) {
        NioFiles.deleteRecursively(externalSystemCache)
      }

      val newRecoveryScope = openProject(projectPath)

      AsyncRecoveryResult(newRecoveryScope, emptyList())
    }.asCompletableFuture()
  }

  private suspend fun invalidateLocalExternalSystemCache(recoveryScope: RecoveryScope) {
    blockingContext {
      val project = recoveryScope.project
      for (manager in ExternalSystemApiUtil.getAllManagers()) {
        val localSettings = manager.getLocalSettingsProvider().`fun`(project)
        localSettings.invalidateCaches()
      }
    }
  }

  private suspend fun invalidateProjectBuildClasspathCache(recoveryScope: RecoveryScope) {
    recoveryScope.project.workspaceModel.update("Invalidate project build classpath cache") { storage ->
      storage.entities<ExternalProjectsBuildClasspathEntity>()
        .forEach { storage.removeEntity(it) }
    }
  }

  private suspend fun invalidateExternalSystemDataStorage(recoveryScope: RecoveryScope) {
    blockingContext {
      val project = recoveryScope.project
      val dataStorage = ExternalProjectsDataStorage.getInstance(project)
      for (manager in ExternalSystemApiUtil.getAllManagers()) {
        val systemId = manager.getSystemId()
        val settings = manager.getSettingsProvider().`fun`(project)
        for (projectSettings in settings.linkedProjectsSettings) {
          val externalProjectPath = projectSettings.externalProjectPath
          dataStorage.remove(systemId, externalProjectPath)
        }
      }
    }
  }

  private suspend fun invalidateExternalSystemToolwindow(recoveryScope: RecoveryScope) {
    blockingContextScope {
      val project = recoveryScope.project
      for (manager in ExternalSystemApiUtil.getAllManagers()) {
        val systemId = manager.getSystemId()
        ExternalSystemUtil.scheduleExternalViewStructureUpdate(project, systemId)
      }
    }
  }

  private suspend fun closeProject(recoveryScope: RecoveryScope): Path {
    val projectPath = Path.of(recoveryScope.project.basePath!!)
    withContext(Dispatchers.EDT) {
      blockingContext {
        val projectManager = ProjectManager.getInstance()
        projectManager.closeAndDispose(recoveryScope.project)
      }
    }
    return projectPath
  }

  private suspend fun openProject(projectPath: Path): RecoveryScope {
    val project = ProjectUtil.openOrImportAsync(
      file = projectPath,
      options = OpenProjectTask {
        runConfigurators = true
        isNewProject = !ProjectUtilCore.isValidProjectPath(projectPath)
        useDefaultProjectAsTemplate = true
      }
    )!!
    return ProjectRecoveryScope(project)
  }

  @Service(Service.Level.APP)
  private class CoroutineScopeService(val coroutineScope: CoroutineScope) {
    companion object {
      fun coroutineScope(): CoroutineScope {
        return application.service<CoroutineScopeService>().coroutineScope
      }
    }
  }
}