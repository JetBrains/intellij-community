// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.settings

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo
import com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntity
import com.intellij.openapi.externalSystem.settings.workspaceModel.getExternalProjectBuildClasspathPojo
import com.intellij.openapi.externalSystem.settings.workspaceModel.getExternalProjectsBuildClasspathEntity
import com.intellij.openapi.externalSystem.settings.workspaceModel.modifyExternalProjectsBuildClasspathEntity
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.ui.EDT.*
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ProjectBuildClasspathManager(val project: Project, val coroutineScope: CoroutineScope) {

  private fun getAvailableProjectsPaths(): List<String> = ExternalSystemApiUtil.getAllManagers()
    .map { it.localSettingsProvider.`fun`(project) }
    .flatMap { it.availableProjects.keys }
    .map { it.path }

  fun setProjectBuildClasspathSync(value: Map<String, ExternalProjectBuildClasspathPojo>) {
    // Android tests' setup undate project build classpath on EDTs
    if (isCurrentThreadEdt()) {
      runWriteAction { setProjectBuildClasspathInWriteAction(value) }
    } else {
      runBlockingCancellable {
        setProjectBuildClasspath(value)
      }
    }
  }

  suspend fun setProjectBuildClasspath(value: Map<String, ExternalProjectBuildClasspathPojo>) {
    project.workspaceModel
      .update("AbstractExternalSystemLocalSettings async update") { storage ->
        updateOrAddValueToStorage(value, storage)
      }
  }

  fun setProjectBuildClasspathInWriteAction(value: Map<String, ExternalProjectBuildClasspathPojo>) {
    project.workspaceModel
      .updateProjectModel("AbstractExternalSystemLocalSettings explicit update") { storage ->
        updateOrAddValueToStorage(value, storage)
      }
  }

  private fun updateOrAddValueToStorage(
    value: Map<String, ExternalProjectBuildClasspathPojo>,
    storage: MutableEntityStorage,
  ) {
    val newClasspathEntity = getExternalProjectsBuildClasspathEntity(value)
    val buildClasspathEntity = storage.entities(ExternalProjectsBuildClasspathEntity::class.java).firstOrNull()

    if (buildClasspathEntity != null) {
      storage.modifyExternalProjectsBuildClasspathEntity(buildClasspathEntity) {
        projectsBuildClasspath = newClasspathEntity.projectsBuildClasspath
      }
    }
    else {
      storage.addEntity(newClasspathEntity)
    }
  }

  fun getProjectBuildClasspath(): Map<String, ExternalProjectBuildClasspathPojo> {
    return WorkspaceModel.getInstance(project)
             .currentSnapshot
             .entities(ExternalProjectsBuildClasspathEntity::class.java)
             .firstOrNull()
             ?.let { getExternalProjectBuildClasspathPojo(it) }  ?: return Collections.emptyMap()
  }

  fun removeUnavailableClasspaths() {
    val availableProjectsPaths = getAvailableProjectsPaths().toSet()
    val currentClasspath = getProjectBuildClasspath()
    val updatedClasspath = currentClasspath.filterKeys { it in availableProjectsPaths }
    setProjectBuildClasspathSync(updatedClasspath)
  }
}