// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.lang.JavaVersion

internal class DefaultWorkspaceSettingsImporter : WorkspaceSettingsImporter {
  override fun importFromProject(project: Project): ImportedProjectSettings {
    return DefaultImportedProjectSettings(project)
  }
}

private class DefaultImportedProjectSettings(project: Project) : ImportedProjectSettings {
  private val projectSdk: Sdk?

  init {
    projectSdk = try {
      ProjectRootManager.getInstance(project).projectSdk?.clone()
    }
    catch (ignore: CloneNotSupportedException) {
      null
    }
  }

  override suspend fun applyTo(workspace: Project): Boolean {
    if (projectSdk != null) {
      val current = ProjectRootManager.getInstance(workspace).projectSdk
      if (current == null || getVersion(current) < getVersion(projectSdk)) {
        writeAction {
          ProjectRootManager.getInstance(workspace).projectSdk = projectSdk
        }
      }
    }
    return false
  }

  private fun getVersion(sdk: Sdk): JavaVersion = JavaVersion.tryParse(sdk.versionString) ?: JavaVersion.compose(4)
}
