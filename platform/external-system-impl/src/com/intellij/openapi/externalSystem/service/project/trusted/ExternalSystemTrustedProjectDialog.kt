// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.trusted

import com.intellij.ide.IdeBundle
import com.intellij.ide.trustedProjects.TrustedProjectsDialog
import com.intellij.ide.trustedProjects.TrustedProjectsDialog.confirmOpeningOrLinkingUntrustedProject
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

object ExternalSystemTrustedProjectDialog {

  suspend fun confirmLinkingUntrustedProjectAsync(
    project: Project,
    systemId: ProjectSystemId,
    projectRoot: Path
  ): Boolean {
    return confirmOpeningOrLinkingUntrustedProject(
      projectRoot = projectRoot,
      project = project,
      title = IdeBundle.message("untrusted.project.link.dialog.title", systemId, projectRoot.fileName ?: projectRoot.toString()),
      cancelButtonText = IdeBundle.message("untrusted.project.link.dialog.cancel.button"))
  }

  suspend fun confirmLoadingUntrustedProjectAsync(
    project: Project,
    systemId: ProjectSystemId
  ): Boolean {
    return confirmLoadingUntrustedProjectAsync(project, listOf(systemId))
  }

  suspend fun confirmLoadingUntrustedProjectAsync(
    project: Project,
    systemIds: Collection<ProjectSystemId>
  ): Boolean {
    val systemsPresentation = ExternalSystemUtil.naturalJoinSystemIds(systemIds)
    return TrustedProjectsDialog.confirmLoadingUntrustedProjectAsync(
      project = project,
      title = IdeBundle.message("untrusted.project.dialog.title", systemsPresentation, systemIds.size),
      message = IdeBundle.message("untrusted.project.dialog.text", systemsPresentation, systemIds.size),
    )
  }

  @JvmStatic
  @ApiStatus.Obsolete
  fun confirmLoadingUntrustedProject(
    project: Project,
    systemId: ProjectSystemId
  ): Boolean {
    return confirmLoadingUntrustedProject(project, listOf(systemId))
  }

  @JvmStatic
  @ApiStatus.Obsolete
  fun confirmLoadingUntrustedProject(
    project: Project,
    systemIds: Collection<ProjectSystemId>
  ): Boolean {
    val systemsPresentation = ExternalSystemUtil.naturalJoinSystemIds(systemIds)
    return TrustedProjectsDialog.confirmLoadingUntrustedProject(
      project = project,
      title = IdeBundle.message("untrusted.project.dialog.title", systemsPresentation, systemIds.size),
      message = IdeBundle.message("untrusted.project.dialog.text", systemsPresentation, systemIds.size),
    )
  }
}