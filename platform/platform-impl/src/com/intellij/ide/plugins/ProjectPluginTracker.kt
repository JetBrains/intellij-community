// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.xmlb.annotations.XCollection

class ProjectPluginTracker(private val project: Project,
                                    private val state: ProjectPluginTrackerState) {

  companion object {

    class ProjectPluginTrackerState : BaseState() {

      @get:XCollection
      internal var enabledPlugins by stringSet()

      @get:XCollection
      internal var disabledPlugins by stringSet()

      fun register(id: PluginId, enable: Boolean) {
        val idString = id.idString
        if (!setToRemoveFrom(enable).remove(idString)) {
          setToAddTo(enable).add(idString)
        }
      }

      fun unregister(id: PluginId) {
        val idString = id.idString
        if (!enabledPlugins.remove(idString)) {
          disabledPlugins.remove(idString)
        }
      }

      internal fun updatePluginEnabledState(project: Project, enable: Boolean) {
        PluginEnabler.updatePluginEnabledState(
          project,
          setToAddTo(enable).findPluginById(),
          setToRemoveFrom(enable).findPluginById(),
          null,
          false,
        )
      }

      private fun setToAddTo(enable: Boolean) = if (enable) enabledPlugins else disabledPlugins

      private fun setToRemoveFrom(enable: Boolean) = if (enable) disabledPlugins else enabledPlugins

      private fun Set<String>.findPluginById() = mapNotNull { PluginId.findId(it) }.mapNotNull { PluginManagerCore.getPlugin(it) }
    }

    internal class EnableDisablePluginsActivity : StartupActivity.RequiredForSmartMode {

      init {
        if (getApplication().isUnitTestMode) {
          throw ExtensionNotApplicableException.INSTANCE
        }
      }

      override fun runActivity(project: Project) {
        ProjectPluginTrackerManager.getInstance()
          .createPluginTracker(project)
          .updatePluginEnabledState(true)
      }
    }
  }

  fun changeEnableDisable(pluginId: PluginId, newState: PluginEnabledState) {
    if (newState.isPerProject) {
      state.register(pluginId, enable = newState.isEnabled)
    }
    else {
      state.unregister(pluginId)
    }
  }

  fun isEnabled(pluginId: PluginId) = state.enabledPlugins.contains(pluginId.idString)

  fun isDisabled(pluginId: PluginId) = state.disabledPlugins.contains(pluginId.idString)

  internal fun updatePluginEnabledState(enable: Boolean) = state.updatePluginEnabledState(project, enable)
}
