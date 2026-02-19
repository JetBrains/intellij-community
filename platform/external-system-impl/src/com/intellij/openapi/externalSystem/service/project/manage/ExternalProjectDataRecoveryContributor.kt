// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntity
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.entities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
class ExternalProjectDataRecoveryContributor : ExternalSystemRecoveryContributor {

  private lateinit var myExternalSystemCache: Path

  override suspend fun beforeClose(recoveryScope: RecoveryScope) {
    invalidateLocalExternalSystemCache(recoveryScope)
    invalidateProjectBuildClasspathCache(recoveryScope)
    invalidateExternalSystemDataStorage(recoveryScope)
    invalidateExternalSystemToolwindow(recoveryScope)
    myExternalSystemCache = ExternalProjectsDataStorage.getProjectConfigurationDir(recoveryScope.project)
  }

  override suspend fun afterClose() {
    withContext(Dispatchers.IO) {
      NioFiles.deleteRecursively(myExternalSystemCache)
    }
  }

  private suspend fun invalidateLocalExternalSystemCache(recoveryScope: RecoveryScope) {
    val project = recoveryScope.project
    for (manager in ExternalSystemApiUtil.getAllManagers()) {
      val localSettings = manager.getLocalSettingsProvider().`fun`(project)
      localSettings.invalidateCaches()
    }
  }

  private suspend fun invalidateProjectBuildClasspathCache(recoveryScope: RecoveryScope) {
    recoveryScope.project.workspaceModel.update("Invalidate project build classpath cache") { storage ->
      storage.entities<ExternalProjectsBuildClasspathEntity>()
        .forEach { storage.removeEntity(it) }
    }
  }

  private suspend fun invalidateExternalSystemDataStorage(recoveryScope: RecoveryScope) {
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

  private suspend fun invalidateExternalSystemToolwindow(recoveryScope: RecoveryScope) {
    blockingContextScope {
      val project = recoveryScope.project
      for (manager in ExternalSystemApiUtil.getAllManagers()) {
        val systemId = manager.getSystemId()
        ExternalSystemUtil.scheduleExternalViewStructureUpdate(project, systemId)
      }
    }
  }

  class Factory : ExternalSystemRecoveryContributor.Factory {
    override fun createContributor(): ExternalSystemRecoveryContributor {
      return ExternalProjectDataRecoveryContributor()
    }
  }
}