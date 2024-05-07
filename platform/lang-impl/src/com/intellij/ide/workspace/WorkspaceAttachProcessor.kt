// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.lang.LangBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import java.nio.file.Path
import kotlin.io.path.pathString

class WorkspaceAttachProcessor : ProjectAttachProcessor() {
  override fun attachToProject(project: Project, projectDir: Path, callback: ProjectOpenedCallback?): Boolean {
    if (project.isWorkspace) {
      linkToWorkspace(project, projectDir.pathString)
      return true
    }
    else {
      return createWorkspace(project)
    }
  }

  override val isEnabled: Boolean = Registry.`is`("ide.enable.project.workspaces")

  override fun getActionText(project: Project): String {
    if (project.isWorkspace) {
      return LangBundle.message("prompt.open.project.attach.to.workspace.button.attach")
    }
    else {
      return LangBundle.message("prompt.open.project.create.workspace.button.attach")
    }
  }

  override fun getDescription(project: Project): String {
    if (project.isWorkspace) {
      return LangBundle.message("prompt.open.project.attach.to.workspace")
    }
    else {
      return LangBundle.message("prompt.open.project.create.workspace")
    }
  }
}