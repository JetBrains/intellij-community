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
)
@ApiStatus.Internal
class ProjectPluginTrackerManager : SimplePersistentStateComponent<ProjectPluginTrackerManager.Companion.ProjectPluginTrackerManagerState>(
  ProjectPluginTrackerManagerState()
) {

  companion object {

    @JvmStatic
    fun getInstance() = service<ProjectPluginTrackerManager>()

    @JvmStatic
    internal fun loadPlugins(candidatesToLoad: Collection<PluginId>) {
      loadPlugins(
        candidatesToLoad.findPluginById()
      )
    }

    @JvmStatic
    private fun loadPlugins(descriptors: Collection<IdeaPluginDescriptor>): Boolean {
      /*
     model: enabled (descriptor, disabledList, load), enabled per project (descriptor, load)
     project opening: enabled per project (descriptor, load)
     project closing: disabled per project (descriptor, load iff not is disabled globally)
     */

      return descriptors.isEmpty() ||
             DynamicPlugins.loadPlugins(descriptors).requireRestartIfNecessary()
    }

    class ProjectPluginTrackerManagerState : BaseState() {

      @get:XCollection
      var trackers by map<String, ProjectPluginTracker.Companion.ProjectPluginTrackerState>()
    }

    private fun Boolean.requireRestartIfNecessary(): Boolean {
      if (!this) {
        InstalledPluginsState.getInstance().isRestartRequired = true
      }
      return this
    }

    private fun Collection<PluginId>.findPluginById() = mapNotNull { PluginManagerCore.getPlugin(it) }
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

          loadPlugins(
            tracker.disabledPluginIds(trackers)
          )

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

  fun createPluginTracker(project: Project): ProjectPluginTracker {
    val workspaceId = if (project.isDefault) null else project.stateStore.projectWorkspaceId
    val key = workspaceId ?: project.name
    return ProjectPluginTracker(
      project,
      state.trackers.getOrPut(key) { ProjectPluginTracker.Companion.ProjectPluginTrackerState() },
    )
  }

  fun stopTracking(pluginId: PluginId) {
    state
      .trackers
      .values
      .forEach { it.unregister(pluginId) }
  }

  @JvmOverloads
  fun updatePluginsState(
    descriptors: Collection<IdeaPluginDescriptor>,
    action: PluginEnableDisableAction,
    project: Project? = null,
    parentComponent: JComponent? = null,
  ): Boolean {
    assert(!action.isPerProject || project != null)

    val pluginIds = descriptors.map { it.pluginId }
    val pluginTracker = project?.let { createPluginTracker(it) }

    fun enablePlugins(enabled: Boolean) = DisabledPluginsState.enablePlugins(descriptors, enabled)

    fun startTrackingPerProject(enable: Boolean) = pluginTracker!!.startTrackingPerProject(pluginIds, enable)

    fun stopTrackingPerProject() = pluginTracker?.stopTrackingPerProject(pluginIds)

    fun loadPlugins() = loadPlugins(descriptors)

    fun unloadPlugins() = unloadPlugins(descriptors, project, parentComponent)

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
    project: Project? = null,
  ) {
    unloadPlugins(
      candidatesToUnload.findPluginById(),
      project,
    )
  }

  private fun unloadPlugins(
    descriptors: Collection<IdeaPluginDescriptor>,
    project: Project? = null,
    parentComponent: JComponent? = null,
  ): Boolean {
    /*
    model: disabled (descriptor, disabledList, unload iff not is used), enabled per project (descriptor, unload if not is used)
    project opening: disabled per project (descriptor, unload if not is used)
    project closing: enabled per project (descriptor, unload ???)
     */

    return descriptors.isEmpty() ||
           DynamicPlugins.unloadPlugins(
             descriptors.filter(shouldUnload(project)),
             project,
             parentComponent,
           ).requireRestartIfNecessary()
  }

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
