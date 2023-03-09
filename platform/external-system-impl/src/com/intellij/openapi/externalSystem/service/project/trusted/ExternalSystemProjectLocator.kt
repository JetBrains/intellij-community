// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.trusted

import com.intellij.ide.impl.trustedProjects.ProjectLocator
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.file.CanonicalPathUtil.toNioPath
import com.intellij.openapi.file.converter.NioPathPrefixTreeFactory
import com.intellij.openapi.project.Project
import java.nio.file.Path

class ExternalSystemProjectLocator : ProjectLocator {

  override fun getProjectRoots(project: Project): List<Path> {
    val projectRoots = NioPathPrefixTreeFactory.createSet()
    ExternalSystemManager.EP_NAME.forEachExtensionSafe { manager ->
      val settings = manager.settingsProvider.`fun`(project)
      for (projectSettings in settings.linkedProjectsSettings) {
        projectRoots.add(projectSettings.externalProjectPath.toNioPath())
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