// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectImport

import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Experimental
import java.nio.file.Path

open class ProjectAttachProcessor {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ProjectAttachProcessor> = ExtensionPointName("com.intellij.projectAttachProcessor")

    @JvmStatic
    fun canAttachToProject(): Boolean = getProcessor(null, null, null) != null

    @JvmStatic
    fun getProcessor(project: Project?, path: Path?, newProject: Project?) =
      EP_NAME.extensionList.firstOrNull { it.isEnabled(project, path, newProject) }
  }

  /**
   * Called to attach the directory projectDir as a module to the specified project.
   *
   * @param project    the project to attach the directory to.
   * @param projectDir the directory to attach.
   * @param callback   the callback to call on successful attachment
   * @return true, if the `attach` succeeded, false if the project should be opened in a new window.
   */
  open fun attachToProject(project: Project, projectDir: Path, callback: ProjectOpenedCallback?): Boolean {
    return false
  }

  @Experimental
  open suspend fun attachToProjectAsync(project: Project, projectDir: Path, callback: ProjectOpenedCallback?): Boolean {
    return withContext(Dispatchers.EDT) {
      blockingContext {
        attachToProject(project = project, projectDir = projectDir, callback = callback)
      }
    }
  }

  open suspend fun beforeAttach(project: Project?) {}

  open fun beforeDetach(module: Module) {}

  open fun isEnabled(project: Project?, projectDir: Path?, newProject: Project?): Boolean = true

  open fun getActionText(project: Project): @NlsContexts.Button String? = null

  open fun getDescription(project: Project): @NlsContexts.DetailedDescription String? = null

  open fun defaultOptionIndex(project: Project?): Int = 0
}
