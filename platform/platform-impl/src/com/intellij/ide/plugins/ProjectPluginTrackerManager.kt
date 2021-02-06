// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
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

    @JvmStatic
    @JvmOverloads
    fun unloadPlugins(
      pluginIds: Collection<PluginId>,
      project: Project? = null,
      parentComponent: JComponent? = null,
    ): Boolean = DynamicPlugins.unloadPlugins(
      pluginIds.toPluginDescriptors(),
      project,
      parentComponent,
    )

    internal class EnableDisablePluginsActivity : StartupActivity.RequiredForSmartMode {

      init {
        if (ApplicationManager.getApplication().isUnitTestMode) {
          throw ExtensionNotApplicableException.INSTANCE
        }
      }

      override fun runActivity(project: Project) {
        val tracker = instance.getPluginTrackerImpl(project)
        val trackers = instance.openProjectsPluginTrackers(project)

        loadPlugins(tracker.enabledPluginIds(trackers))

        unloadPlugins(
          tracker.disabledPluginsIds,
          project,
          trackers,
        )
      }
    }

    @JvmStatic
    private fun unloadPlugins(
      pluginIds: Collection<PluginId>,
      project: Project,
      trackers: List<ProjectPluginTracker>,
    ): Boolean = unloadPlugins(
      pluginIds.filter(shouldUnload(trackers)),
      project,
    )

    @JvmStatic
    private fun shouldUnload(trackers: List<ProjectPluginTracker>) = { pluginId: PluginId ->
      trackers.all { !it.isEnabled(pluginId) } &&
      (DisabledPluginsState.isDisabled(pluginId) || trackers.all { it.isDisabled(pluginId) })
    }

    private fun ProjectPluginTrackerImpl.enabledPluginIds(trackers: List<ProjectPluginTracker>): Collection<PluginId> {
      return trackers
        .filterIsInstance<ProjectPluginTrackerImpl>()
        .flatMap { it.disabledPluginIds() }
        .union(enabledPluginsIds)
    }

    private fun ProjectPluginTrackerImpl.disabledPluginIds(trackers: List<ProjectPluginTracker> = listOf()): Collection<PluginId> {
      return disabledPluginsIds
        .filterNot { pluginId ->
          DisabledPluginsState.isDisabled(pluginId) ||
          trackers.isNotEmpty() && trackers.all { it.isDisabled(pluginId) }
        }
    }

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

          val tracker = getPluginTrackerImpl(project)
          val trackers = openProjectsPluginTrackers(project)

          loadPlugins(tracker.disabledPluginIds(trackers))

          unloadPlugins(
            tracker.enabledPluginIds(trackers),
            project,
            trackers,
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

  fun getPluginTracker(project: Project): ProjectPluginTracker = getPluginTrackerImpl(project)

  fun getTrackers(): Map<String, ProjectPluginTracker> = state.trackers

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

    fun unloadPlugins(): Boolean {
      val predicate = when (project) {
        null -> { _: PluginId -> true }
        else -> shouldUnload(openProjectsPluginTrackers(project))
      }

      return DynamicPlugins.unloadPlugins(
        descriptors.filter { predicate.invoke(it.pluginId) },
        project,
        parentComponent,
      )
    }
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

  internal fun openProjectsPluginTrackers(project: Project?): List<ProjectPluginTracker> {
    return ProjectManager.getInstance().openProjects
      .asSequence()
      .filterNot { it == project }
      .map { getPluginTracker(it) }
      .toList()
  }

  private fun getPluginTrackerImpl(project: Project) = state.findStateByProject(project)
}

@ApiStatus.Internal
class ProjectPluginTrackerManagerState : BaseState() {
  @get:XCollection(propertyElementName = "trackers", style = XCollection.Style.v2)
  internal val trackers by map<String, ProjectPluginTrackerImpl>()

  fun startTracking(project: Project, pluginIds: Collection<PluginId>, enable: Boolean) {
    val updated = findStateByProject(project)
      .startTracking(pluginIds, enable)

    if (updated) {
      incrementModificationCount()
    }
  }

  fun stopTracking(pluginIds: Collection<PluginId>) {
    var updated = false

    for (state in trackers.values) {
      updated = state.stopTracking(pluginIds) || updated
    }

    if (updated) {
      incrementModificationCount()
    }
  }

  internal fun findStateByProject(project: Project): ProjectPluginTrackerImpl {
    val workspaceId = if (project.isDefault) null else project.stateStore.projectWorkspaceId
    val projectName = project.name
    return trackers.computeIfAbsent(workspaceId ?: projectName) {
      ProjectPluginTrackerImpl(projectName).also { incrementModificationCount() }
    }
  }
}