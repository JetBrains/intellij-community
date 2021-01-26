// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProjectPluginTracker(
  internal val projectName: String,
  private val state: ProjectPluginTrackerState,
) {

  companion object {

    internal class EnableDisablePluginsActivity : StartupActivity.RequiredForSmartMode {

      init {
        if (getApplication().isUnitTestMode) {
          throw ExtensionNotApplicableException.INSTANCE
        }
      }

      override fun runActivity(project: Project) {
        val manager = ProjectPluginTrackerManager.getInstance()
        val tracker = manager.createPluginTracker(project)
        val trackers = manager.openProjectsPluginTrackers(project)

        ProjectPluginTrackerManager.loadPlugins(
          tracker.enabledPluginIds(trackers)
        )

        manager.unloadPlugins(
          tracker.disabledPlugins.findPluginId(),
          project,
        )
      }
    }

    private fun Set<String>.findPluginId() = mapNotNull { PluginId.findId(it) }
  }

  private val enabledPlugins get() = state.enabledPlugins

  private val disabledPlugins get() = state.disabledPlugins

  fun startTrackingPerProject(
    pluginIds: Iterable<PluginId>,
    enable: Boolean,
  ) {
    val (setToRemoveFrom, setToAddTo) = if (enable)
      Pair(disabledPlugins, enabledPlugins)
    else
      Pair(enabledPlugins, disabledPlugins)

    pluginIds
      .map { it.idString }
      .forEach {
        setToRemoveFrom.remove(it)
        setToAddTo.add(it)
      }
  }

  fun stopTrackingPerProject(pluginIds: Iterable<PluginId>) {
    pluginIds
      .map { it.idString }
      .filterNot { enabledPlugins.remove(it) }
      .forEach { disabledPlugins.remove(it) }
  }

  fun isEnabled(pluginId: PluginId) = enabledPlugins.contains(pluginId.idString)

  fun isDisabled(pluginId: PluginId) = disabledPlugins.contains(pluginId.idString)

  internal fun enabledPluginIds(trackers: List<ProjectPluginTracker>): Collection<PluginId> {
    return trackers
      .flatMap { it.disabledPluginIds() }
      .union(enabledPlugins.findPluginId())
  }

  internal fun disabledPluginIds(trackers: List<ProjectPluginTracker> = listOf()): Collection<PluginId> {
    return disabledPlugins
      .findPluginId()
      .filterNot { pluginId ->
        DisabledPluginsState.isDisabled(pluginId) ||
        trackers.isNotEmpty() && trackers.all { it.isDisabled(pluginId) }
      }
  }
}

class ProjectPluginTrackerState : BaseState() {

  @get:XCollection
  var enabledPlugins by stringSet()

  @get:XCollection
  var disabledPlugins by stringSet()
}