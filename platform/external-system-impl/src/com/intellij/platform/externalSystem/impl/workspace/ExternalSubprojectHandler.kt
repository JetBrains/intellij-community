// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.workspace

import com.intellij.ide.workspace.Subproject
import com.intellij.ide.workspace.SubprojectHandler
import com.intellij.openapi.externalSystem.action.DetachExternalProjectAction
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
abstract class ExternalSubprojectHandler(val systemId: ProjectSystemId): SubprojectHandler {
  override fun getSubprojects(workspace: Project): List<Subproject> {
    val infos = ProjectDataManager.getInstance().getExternalProjectsData(workspace, systemId)
    return infos.map { projectInfo -> ExternalSubproject(projectInfo, this) }
  }

  override fun removeSubprojects(workspace: Project, subprojects: List<Subproject>) {
    subprojects.forEach {
      removeSubproject(workspace, it as ExternalSubproject)
    }
  }

  private fun removeSubproject(workspace: Project, subproject: ExternalSubproject) {
    val info = requireNotNull(subproject.projectInfo)
    val data = requireNotNull(info.externalProjectStructure?.data)
    DetachExternalProjectAction.detachProject(workspace, info.projectSystemId, data, null)
  }
}