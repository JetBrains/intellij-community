// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.lang.LangBundle
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.project.stateStore
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.io.path.pathString

internal class WorkspaceAttachProcessor : ProjectAttachProcessor() {
  override fun attachToProject(project: Project, projectDir: Path, callback: ProjectOpenedCallback?): Boolean {
    if (!project.isWorkspace) {
      return false
    }
    getCoroutineScope(project).launch {
      val modules = ModuleManager.getInstance(project).modules.toSet()
      linkToWorkspace(project, projectDir.pathString)
      if (callback != null) {
        ModuleManager.getInstance(project).modules.subtract(modules).firstOrNull()?.let {
          module -> callback.projectOpened(project, module)
        }
      }
    }
    return true
  }

  override suspend fun beforeAttach(project: Project?) {
    project?.stateStore?.save(true)
  }

  override fun isEnabled(project: Project?, path: Path?, newProject: Project?): Boolean =
    isWorkspaceSupportEnabled && project?.isWorkspace == true &&
    (path == null || !getAllSubprojects(project).any { it.projectPath == path.pathString }) &&
    (newProject == null || !newProject.isWorkspace)

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

  override val defaultOptionIndex = 2
}