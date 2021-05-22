// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui

import com.intellij.icons.AllIcons.General.ProjectConfigurable
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.ProjectPluginTrackerManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

class ProjectDependentPluginEnabledState(
  private val pluginId: PluginId,
  private val state: PluginEnabledState,
  private val project: Project?,
) {

  private var _projectNames: String? = null
  private val projectNames: String
    get() {
      if (_projectNames == null) {
        _projectNames = if (isEnabled)
          ""
        else
          ProjectPluginTrackerManager.openProjectsExcludingCurrent(project)
            .map { ProjectPluginTrackerManager.instance.getPluginTracker(it) }
            .filter { !PluginManagerCore.isDisabled(pluginId) || it.isEnabled(pluginId) }
            .joinToString(limit = 3) { "<code>${it.projectName}</code>" }
      }
      return _projectNames ?: throw IllegalStateException("Should not be used outside EDT")
    }

  val isEnabled get() = state.isEnabled

  val icon get() = if (state.isPerProject) ProjectConfigurable else null

  @Nls
  override fun toString(): String {
    val stateText = state.toString()

    return if (projectNames.isEmpty())
      stateText
    else
      IdeBundle.message(
        "plugins.configurable.loaded.for.projects",
        stateText,
        projectNames
      )
  }

}