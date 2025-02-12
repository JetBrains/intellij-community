// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.trusted

import com.intellij.ide.IdeBundle
import com.intellij.ide.trustedProjects.TrustedProjectsDialog
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import java.nio.file.Path

object ExternalSystemTrustedProjectDialog {

  suspend fun confirmLinkingUntrustedProjectAsync(
    project: Project,
    systemId: ProjectSystemId,
    projectRoot: Path
  ): Boolean {
    return TrustedProjectsDialog.confirmOpeningOrLinkingUntrustedProject(
      projectRoot,
      project,
      title = IdeBundle.message("untrusted.project.link.dialog.title", systemId.readableName, projectRoot.fileName ?: projectRoot.toString()),
      cancelButtonText = IdeBundle.message("untrusted.project.link.dialog.cancel.button"))
  }

  suspend fun confirmLoadingUntrustedProjectAsync(
    project: Project,
    systemId: ProjectSystemId
  ): Boolean {
    return confirmLoadingUntrustedProjectAsync(project, listOf(systemId))
  }

  private suspend fun confirmLoadingUntrustedProjectAsync(
    project: Project,
    systemIds: Collection<ProjectSystemId>
  ): Boolean {
    val systemsPresentation = ExternalSystemUtil.naturalJoinSystemIds(systemIds)
    return TrustedProjectsDialog.confirmLoadingUntrustedProjectAsync(
      project,
      IdeBundle.message("untrusted.project.dialog.title", systemsPresentation, systemIds.size),
      IdeBundle.message("untrusted.project.dialog.text", systemsPresentation, systemIds.size),
      IdeBundle.message("untrusted.project.dialog.trust.button"),
      IdeBundle.message("untrusted.project.dialog.distrust.button")
    )
  }

  @JvmStatic
  @Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
  @Deprecated("Use async method instead")
  fun confirmLoadingUntrustedProject(
    project: Project,
    systemId: ProjectSystemId
  ): Boolean {
    return confirmLoadingUntrustedProject(project, listOf(systemId))
  }

  @JvmStatic
  @Suppress("DEPRECATION")
  @Deprecated("Use async method instead")
  fun confirmLoadingUntrustedProject(
    project: Project,
    systemIds: Collection<ProjectSystemId>
  ): Boolean {
    val systemsPresentation = ExternalSystemUtil.naturalJoinSystemIds(systemIds)
    return TrustedProjectsDialog.confirmLoadingUntrustedProject(
      project,
      IdeBundle.message("untrusted.project.dialog.title", systemsPresentation, systemIds.size),
      IdeBundle.message("untrusted.project.dialog.text", systemsPresentation, systemIds.size),
      IdeBundle.message("untrusted.project.dialog.trust.button"),
      IdeBundle.message("untrusted.project.dialog.distrust.button")
    )
  }
}