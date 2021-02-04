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
        val manager = ProjectPluginTrackerManager.instance
        val tracker = manager.createPluginTracker(project)
        val trackers = manager.openProjectsPluginTrackers(project)

        ProjectPluginTrackerManager.loadPlugins(tracker.enabledPluginIds(trackers))

        manager.unloadPlugins(
          tracker.state.disabledPluginsIds,
          project,
        )
      }
    }
  }

  fun isEnabled(pluginId: PluginId) = state.enabledPlugins.contains(pluginId.idString)

  fun isDisabled(pluginId: PluginId) = state.disabledPlugins.contains(pluginId.idString)

  internal fun enabledPluginIds(trackers: List<ProjectPluginTracker>): Collection<PluginId> {
    return trackers
      .flatMap { it.disabledPluginIds() }
      .union(state.enabledPluginsIds)
  }

  internal fun disabledPluginIds(trackers: List<ProjectPluginTracker> = listOf()): Collection<PluginId> {
    return state
      .disabledPluginsIds
      .filterNot { pluginId ->
        DisabledPluginsState.isDisabled(pluginId) ||
        trackers.isNotEmpty() && trackers.all { it.isDisabled(pluginId) }
      }
  }
}

class ProjectPluginTrackerState : BaseState() {

  @get:XCollection
  var enabledPlugins by stringSet()

  val enabledPluginsIds: Set<PluginId> get() = enabledPlugins.toPluginIds()

  @get:XCollection
  var disabledPlugins by stringSet()

  val disabledPluginsIds: Set<PluginId> get() = disabledPlugins.toPluginIds()

  fun startTracking(
    pluginIds: Iterable<PluginId>,
    enable: Boolean,
  ): Boolean {
    val (setToRemoveFrom, setToAddTo) = if (enable)
      Pair(disabledPlugins, enabledPlugins)
    else
      Pair(enabledPlugins, disabledPlugins)

    var updated = false

    pluginIds
      .map { it.idString }
      .forEach {
        setToRemoveFrom.remove(it)
        setToAddTo.add(it)
        updated = true
      }

    if (updated) incrementModificationCount()
    return updated
  }

  fun stopTracking(pluginIds: Iterable<PluginId>): Boolean {
    var updated = false

    pluginIds
      .map { it.idString }
      .filterNot {
        val removed = enabledPlugins.remove(it)
        updated = removed || updated
        removed
      }.forEach {
        disabledPlugins.remove(it)
        updated = true
      }

    if (updated) incrementModificationCount()
    return updated
  }

  private fun Set<String>.toPluginIds() = mapNotNullTo(HashSet()) { PluginId.findId(it) }
}