// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.settings

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
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Manages the build classpath for external projects within a specific project context.
 *
 * This service provides functionality to read and update the build classpath configurations
 * for external projects associated with the current IntelliJ project.
 * To remove outdated information (e.g., a project that is no longer linked to an IntelliJ project), an explicit call to
 * [removeUnavailableClasspaths] is required.
 * This will remove any information of unavailable (or not yet available!) projects.
 * <br>Methods of this service are not thread-safe.
 * Get- and Set- method calls should be guarded by a lock on the client side.
 *
 * @ApiStatus.Internal
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ProjectBuildClasspathManager(val project: Project, val coroutineScope: CoroutineScope) {

  @RequiresBackgroundThread
  fun setProjectBuildClasspathSync(value: Map<String, ExternalProjectBuildClasspathPojo>) {
    runBlockingCancellable {
      setProjectBuildClasspath(value)
    }
  }

  suspend fun setProjectBuildClasspath(value: Map<String, ExternalProjectBuildClasspathPojo>) {
    project.workspaceModel
      .update("AbstractExternalSystemLocalSettings async update") { storage ->
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

  private fun getAvailableProjectsPaths(): List<String> = ExternalSystemApiUtil.getAllManagers()
    .map { it.localSettingsProvider.`fun`(project) }
    .flatMap { it.availableProjects.keys }
    .map { it.path }
}