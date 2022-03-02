// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.autoimport

import com.intellij.openapi.extensions.ExtensionPointUtil
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

internal class ExternalProjectsSettingsWatcher : ExternalSystemSettingsListenerEx {
  override fun onProjectsLoaded(
    project: Project,
    manager: ExternalSystemManager<*, *, *, *, *>,
    settings: Collection<ExternalProjectSettings>
  ) {
    if (manager !is ExternalSystemAutoImportAware) {
      return
    }

    val projectTracker = ExternalSystemProjectTracker.getInstance(project)
    val systemId = manager.systemId
    for (projectSettings in settings) {
      projectTracker.activate(ExternalSystemProjectId(systemId = systemId, externalProjectPath = projectSettings.externalProjectPath))
    }
  }

  override fun onProjectsLinked(
    project: Project,
    manager: ExternalSystemManager<*, *, *, *, *>,
    settings: Collection<ExternalProjectSettings>
  ) {
    if (manager !is ExternalSystemAutoImportAware) {
      return
    }

    val extensionDisposable = ExtensionPointUtil.createExtensionDisposable(manager, ExternalSystemManager.EP_NAME)
    Disposer.register(project, extensionDisposable)
    val projectTracker = ExternalSystemProjectTracker.getInstance(project)
    val systemId = manager.systemId
    for (projectSettings in settings) {
      val id = ExternalSystemProjectId(systemId = systemId, externalProjectPath = projectSettings.externalProjectPath)
      projectTracker.register(ProjectAware(project, id, manager), extensionDisposable)
    }
  }

  override fun onProjectsUnlinked(
    project: Project,
    manager: ExternalSystemManager<*, *, *, *, *>,
    linkedProjectPaths: Set<String>
  ) {
    if (manager !is ExternalSystemAutoImportAware) {
      return
    }

    val projectTracker = ExternalSystemProjectTracker.getInstance(project)
    val systemId = manager.systemId
    for (linkedProjectPath in linkedProjectPaths) {
      projectTracker.remove(ExternalSystemProjectId(systemId, linkedProjectPath))
    }
  }
}