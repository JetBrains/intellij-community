// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo
import com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntity
import com.intellij.openapi.externalSystem.settings.workspaceModel.getExternalProjectBuildClasspathPojo
import com.intellij.openapi.externalSystem.settings.workspaceModel.getExternalProjectsBuildClasspathEntity
import com.intellij.openapi.externalSystem.settings.workspaceModel.modifyExternalProjectsBuildClasspathEntity
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ProjectBuildClasspathManager(val project: Project, val coroutineScope: CoroutineScope ) {

  fun setProjectBuildClasspathAsync(value: Map<String, ExternalProjectBuildClasspathPojo>) {
    coroutineScope.launch {
      setProjectBuildClasspath(value)
    }
  }
  suspend fun setProjectBuildClasspath(value: Map<String, ExternalProjectBuildClasspathPojo>) {
      project.workspaceModel
      .update("AbstractExternalSystemLocalSettings update") { storage ->
        val newClasspathEntity = getExternalProjectsBuildClasspathEntity(value)
        val buildClasspathEntity = storage.entities(ExternalProjectsBuildClasspathEntity::class.java).firstOrNull()

        if (buildClasspathEntity != null) {
          storage.modifyExternalProjectsBuildClasspathEntity(buildClasspathEntity) {
            projectsBuildClasspath = newClasspathEntity.projectsBuildClasspath
          }
        } else {
          storage.addEntity(newClasspathEntity)
        }
      }
  }

  fun getProjectBuildClasspath(): Map<String, ExternalProjectBuildClasspathPojo> {
    val availableProjectsPaths = ExternalSystemApiUtil.getAllManagers()
      .map { it.localSettingsProvider.`fun`(project) }
      .flatMap { it.availableProjects.keys }
      .map { it.path }

    return WorkspaceModel.getInstance(project)
      .currentSnapshot
      .entities(ExternalProjectsBuildClasspathEntity::class.java)
      .firstOrNull()
      ?.let { getExternalProjectBuildClasspathPojo(it) }
      ?.let { it.filterKeys { availableProjectsPaths.contains(it) }}
           ?: return Collections.emptyMap()
  }
}