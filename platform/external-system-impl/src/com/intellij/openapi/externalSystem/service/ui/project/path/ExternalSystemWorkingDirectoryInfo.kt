// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.project.path

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.ui.getModelPath
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project

class ExternalSystemWorkingDirectoryInfo(project: Project, externalSystemId: ProjectSystemId) : WorkingDirectoryInfo {
  override val settingsLabel: String = ExternalSystemBundle.message("run.configuration.project.path.label", externalSystemId.readableName)

  override val settingsName: String = ExternalSystemBundle.message("run.configuration.project.path.name", externalSystemId.readableName)
  override val settingsGroup: String? = null
  override val settingsPriority: Int = -10
  override val settingsHint: String? = null
  override val settingsActionHint: String? = null

  override val fileChooserTitle: String = ExternalSystemBundle.message("settings.label.select.project", externalSystemId.readableName)
  override val fileChooserDescription: String? = null
  override val fileChooserDescriptor: FileChooserDescriptor =
    ExternalSystemApiUtil.getExternalProjectConfigDescriptor(externalSystemId)

  override val externalProjects: List<ExternalProject> by lazy {
    ArrayList<ExternalProject>().apply {
      val localSettings = ExternalSystemApiUtil.getLocalSettings<AbstractExternalSystemLocalSettings<*>>(project, externalSystemId)
      val uiAware = ExternalSystemUiUtil.getUiAware(externalSystemId)
      for ((parent, children) in localSettings.availableProjects) {
        val parentPath = getModelPath(parent.path)
        val parentName = uiAware.getProjectRepresentationName(project, parentPath, null)
        add(ExternalProject(parentName, parentPath))
        for (child in children) {
          val childPath = getModelPath(child.path)
          if (parentPath == childPath) continue
          val childName = uiAware.getProjectRepresentationName(project, childPath, parentPath)
          add(ExternalProject(childName, childPath))
        }
      }
    }
  }
}