// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.workspace

import com.intellij.ide.workspace.Subproject
import com.intellij.ide.workspace.SubprojectHandler
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
abstract class ExternalSubprojectHandler(val systemId: ProjectSystemId): SubprojectHandler {
  override fun getSubprojects(project: Project): List<Subproject> {
    val infos = ProjectDataManager.getInstance().getExternalProjectsData(project, systemId)
    return infos.map { projectInfo -> ExternalSubproject(project, projectInfo) }
  }
}