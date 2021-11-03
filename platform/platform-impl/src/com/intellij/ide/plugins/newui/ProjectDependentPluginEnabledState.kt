// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui

import com.intellij.icons.AllIcons.General.ProjectConfigurable
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.DynamicPluginEnabler
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.annotations.Nls

class ProjectDependentPluginEnabledState(
  private val pluginId: PluginId,
  private val state: PluginEnabledState,
  private val project: Project?,
) {

  private val projectNames: List<String> by lazy {
    if (isEnabled) {
      emptyList()
    }
    else {
      (PluginEnabler.getInstance() as? DynamicPluginEnabler)?.let { pluginEnabler ->
        ProjectManager.getInstance()
          .openProjects
          .asSequence()
          .filterNot { it == project }
          .map { pluginEnabler.getPluginTracker(it) }
          .filter { !pluginEnabler.isDisabled(pluginId) || it.isEnabled(pluginId) }
          .map { it.projectName }
          .toList()
      } ?: emptyList()
    }
  }

  val isEnabled get() = state.isEnabled

  val icon get() = if (state.isPerProject) ProjectConfigurable else null

  @Nls
  override fun toString(): String {
    val stateText = state.presentableText

    return if (projectNames.isEmpty())
      stateText
    else
      IdeBundle.message(
        "plugins.configurable.loaded.for.projects",
        stateText,
        projectNames.joinToString(limit = 3) { "<code>${it}</code>" }
      )
  }

}