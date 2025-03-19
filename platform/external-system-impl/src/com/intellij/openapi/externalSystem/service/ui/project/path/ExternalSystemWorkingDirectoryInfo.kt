// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.project.path

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getCanonicalPath

class ExternalSystemWorkingDirectoryInfo(
  private val project: Project,
  private val externalSystemId: ProjectSystemId
) : WorkingDirectoryInfo {
  private val readableName = externalSystemId.readableName

  override val editorLabel: String = ExternalSystemBundle.message("run.configuration.project.path.label", readableName)

  override val settingsName: String = ExternalSystemBundle.message("run.configuration.project.path.name", readableName)

  override val fileChooserDescriptor: FileChooserDescriptor =
    ExternalSystemApiUtil.getExternalProjectConfigDescriptor(externalSystemId).withTitle(ExternalSystemBundle.message("settings.label.select.project", readableName))

  override val emptyFieldError: String = ExternalSystemBundle.message("run.configuration.project.path.empty.error", readableName)

  override suspend fun collectExternalProjects(): List<ExternalProject> {
    return blockingContext {
      val externalProjects = ArrayList<ExternalProject>()
      val localSettings = ExternalSystemApiUtil.getLocalSettings<AbstractExternalSystemLocalSettings<*>>(project, externalSystemId)
      val uiAware = ExternalSystemUiUtil.getUiAware(externalSystemId)
      for ((parent, children) in localSettings.availableProjects) {
        val parentPath = getCanonicalPath(parent.path)
        val parentName = uiAware.getProjectRepresentationName(project, parentPath, null)
        externalProjects.add(ExternalProject(parentName, parentPath))
        for (child in children) {
          val childPath = getCanonicalPath(child.path)
          if (parentPath == childPath) continue
          val childName = uiAware.getProjectRepresentationName(project, childPath, parentPath)
          externalProjects.add(ExternalProject(childName, childPath))
        }
      }
      return@blockingContext externalProjects
    }
  }
}
