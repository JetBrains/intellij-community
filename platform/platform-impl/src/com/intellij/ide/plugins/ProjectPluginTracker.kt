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

    class ProjectPluginTrackerState : BaseState() {

      @get:XCollection
      internal var enabledPlugins by stringSet()

      @get:XCollection
      internal var disabledPlugins by stringSet()

      internal fun register(id: PluginId, enable: Boolean) {
        val idString = id.idString

        val setToRemoveFrom = if (enable) disabledPlugins else enabledPlugins
        setToRemoveFrom.remove(idString)

        val setToAddTo = if (enable) enabledPlugins else disabledPlugins
        setToAddTo.add(idString)
      }

      internal fun unregister(id: PluginId) {
        val idString = id.idString
        if (!enabledPlugins.remove(idString)) {
          disabledPlugins.remove(idString)
        }
      }
    }

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
          tracker.disabledPluginIds,
          project,
        )
      }
    }
  }

  private val enabledPluginIds get() = state.enabledPlugins.findPluginId()

  private val disabledPluginIds get() = state.disabledPlugins.findPluginId()

  fun startTrackingPerProject(
    pluginIds: Iterable<PluginId>,
    enable: Boolean,
  ) {
    pluginIds.forEach { state.register(it, enable) }
  }

  fun stopTrackingPerProject(pluginIds: Iterable<PluginId>) {
    pluginIds.forEach(state::unregister)
  }

  fun isEnabled(pluginId: PluginId) = state.enabledPlugins.contains(pluginId.idString)

  fun isDisabled(pluginId: PluginId) = state.disabledPlugins.contains(pluginId.idString)

  internal fun enabledPluginIds(trackers: List<ProjectPluginTracker>): Collection<PluginId> {
    return trackers
      .flatMap { it.disabledPluginIds() }
      .union(enabledPluginIds)
  }

  internal fun disabledPluginIds(trackers: List<ProjectPluginTracker> = listOf()): Collection<PluginId> {
    return disabledPluginIds
      .filterNot { pluginId ->
        DisabledPluginsState.isDisabled(pluginId) ||
        trackers.isNotEmpty() && trackers.all { it.isDisabled(pluginId) }
      }
  }

  private fun Set<String>.findPluginId() = mapNotNull { PluginId.findId(it) }
}
