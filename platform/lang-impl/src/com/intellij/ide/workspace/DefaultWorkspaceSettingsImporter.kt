// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.impl.isTrusted
import com.intellij.ide.impl.setTrusted
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager

class DefaultWorkspaceSettingsImporter : WorkspaceSettingsImporter {
  override fun importFromProject(project: Project, newWorkspace: Boolean): ImportedProjectSettings? {
    if (newWorkspace) {
      return DefaultImportedProjectSettings(project)
    }
    else {
      return null
    }
  }
}

private class DefaultImportedProjectSettings(project: Project) : ImportedProjectSettings {
  private val isTrusted : Boolean
  private val projectSdk: Sdk?

  init {
    isTrusted = project.isTrusted()

    projectSdk = try {
      ProjectRootManager.getInstance(project).projectSdk?.clone() as? Sdk
    }
    catch (ignore: CloneNotSupportedException) {
      null
    }
  }

  override suspend fun applyTo(workspace: Project) {
    if (isTrusted) {
      workspace.setTrusted(isTrusted)
    }

    if (projectSdk != null) {
      writeAction {
        ProjectRootManager.getInstance(workspace).projectSdk = projectSdk
      }
    }
  }
}
