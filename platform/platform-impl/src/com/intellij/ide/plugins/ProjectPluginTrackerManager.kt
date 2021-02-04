// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.project.stateStore
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@Service
@State(
  name = "ProjectPluginTrackerManager",
  storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)],
  reloadable = false,
)
@ApiStatus.Internal
class ProjectPluginTrackerManager : SimplePersistentStateComponent<ProjectPluginTrackerManagerState>(ProjectPluginTrackerManagerState()) {

  companion object {

    @JvmStatic
    val instance
      get() = service<ProjectPluginTrackerManager>()

    @JvmStatic
    fun loadPlugins(pluginIds: Collection<PluginId>): Boolean = DynamicPlugins.loadPlugins(pluginIds.toPluginDescriptors())

    private fun Collection<PluginId>.toPluginDescriptors() = mapNotNull { PluginManagerCore.getPlugin(it) }
  }

  private var applicationShuttingDown = false

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(
      ProjectManager.TOPIC,
      object : ProjectManagerListener {
        override fun projectClosing(project: Project) {
          if (applicationShuttingDown) return

          val tracker = createPluginTracker(project)
          val trackers = openProjectsPluginTrackers(project)

          loadPlugins(tracker.disabledPluginIds(trackers))

          unloadPlugins(
            tracker.enabledPluginIds(trackers),
            project,
          )
        }
      }
    )

    connection.subscribe(
      AppLifecycleListener.TOPIC,
      object : AppLifecycleListener {
        override fun appWillBeClosed(isRestart: Boolean) {
          applicationShuttingDown = true
        }
      }
    )
  }

  fun createPluginTracker(project: Project) = ProjectPluginTracker(
    project.name,
    state.findStateByProject(project),
  )

  @JvmOverloads
  fun updatePluginsState(
    descriptors: Collection<IdeaPluginDescriptor>,
    action: PluginEnableDisableAction,
    project: Project? = null,
    parentComponent: JComponent? = null,
  ): Boolean {
    assert(!action.isPerProject || project != null)

    val pluginIds = descriptors.map { it.pluginId }

    fun enablePlugins(enabled: Boolean) = DisabledPluginsState.enablePlugins(descriptors, enabled)

    fun startTrackingPerProject(enable: Boolean) = state.startTracking(project!!, pluginIds, enable)

    fun stopTrackingPerProject() = state.stopTracking(pluginIds)

    fun loadPlugins() = DynamicPlugins.loadPlugins(descriptors)

    fun unloadPlugins() = DynamicPlugins.unloadPlugins(
      descriptors.filter(shouldUnload(project)),
      project,
      parentComponent,
    )

    return when (action) {
      PluginEnableDisableAction.ENABLE_GLOBALLY -> {
        enablePlugins(true)
        stopTrackingPerProject()
        loadPlugins()
      }
      PluginEnableDisableAction.ENABLE_FOR_PROJECT -> {
        startTrackingPerProject(true)
        loadPlugins()
      }
      PluginEnableDisableAction.ENABLE_FOR_PROJECT_DISABLE_GLOBALLY -> {
        enablePlugins(false)
        descriptors.forEach { it.isEnabled = true }
        startTrackingPerProject(true)
        true
      }
      PluginEnableDisableAction.DISABLE_GLOBALLY -> {
        enablePlugins(false)
        stopTrackingPerProject()
        unloadPlugins()
      }
      PluginEnableDisableAction.DISABLE_FOR_PROJECT -> {
        startTrackingPerProject(false)
        unloadPlugins()
      }
      PluginEnableDisableAction.DISABLE_FOR_PROJECT_ENABLE_GLOBALLY ->
        false
    }
  }

  internal fun unloadPlugins(
    candidatesToUnload: Collection<PluginId>,
    project: Project,
  ): Boolean = DynamicPlugins.unloadPlugins(
    candidatesToUnload.toPluginDescriptors().filter(shouldUnload(project)),
    project,
  )

  internal fun openProjectsPluginTrackers(project: Project?): List<ProjectPluginTracker> {
    return ProjectManager
      .getInstance()
      .openProjects
      .filterNot { it == project }
      .map { createPluginTracker(it) }
  }

  private fun shouldUnload(project: Project?): (IdeaPluginDescriptor) -> Boolean {
    val trackers = openProjectsPluginTrackers(project)

    return { descriptor ->
      val pluginId = descriptor.pluginId
      trackers.all { !it.isEnabled(pluginId) } &&
      (DisabledPluginsState.isDisabled(pluginId) || trackers.all { it.isDisabled(pluginId) })
    }
  }
}

class ProjectPluginTrackerManagerState : BaseState() {

  @get:XCollection
  var trackers by map<String, ProjectPluginTrackerState>()

  fun startTracking(
    project: Project,
    pluginIds: Collection<PluginId>,
    enable: Boolean,
  ) {
    val updated = findStateByProject(project)
      .startTracking(pluginIds, enable)

    if (updated) incrementModificationCount()
  }

  fun stopTracking(pluginIds: Collection<PluginId>) {
    var updated = false

    trackers.forEach { (_, state) ->
      updated = state.stopTracking(pluginIds) || updated
    }

    if (updated) incrementModificationCount()
  }

  internal fun findStateByProject(project: Project): ProjectPluginTrackerState {
    val workspaceId = if (project.isDefault) null else project.stateStore.projectWorkspaceId

    return trackers.getOrPut(workspaceId ?: project.name) {
      ProjectPluginTrackerState().also { incrementModificationCount() }
    }
  }
}