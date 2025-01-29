// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.trusted

import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.PathPrefixTree
import java.nio.file.Path

private class ExternalSystemTrustedProjectsLocator : TrustedProjectsLocator {
  override fun getProjectRoots(project: Project): List<Path> {
    val projectRoots = PathPrefixTree.createSet()
    ExternalSystemManager.EP_NAME.forEachExtensionSafe { manager ->
      val settings = manager.settingsProvider.`fun`(project)
      for (projectSettings in settings.linkedProjectsSettings) {
        projectRoots.add(Path.of(projectSettings.externalProjectPath))
      }
    }
    return projectRoots.getRoots().toList()
  }

  override fun getProjectRoots(projectRoot: Path, project: Project?): List<Path> {
    if (project == null) {
      return emptyList()
    }
    return getProjectRoots(project)
  }
}