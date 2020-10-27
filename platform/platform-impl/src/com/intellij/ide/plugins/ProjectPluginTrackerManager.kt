// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
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
    fun createPluginTrackerOrNull(project: Project?): ProjectPluginTracker? = project?.let { getInstance().createPluginTracker(it) }

    @JvmStatic
    @JvmOverloads
    fun updatePluginsState(
      descriptors: Collection<IdeaPluginDescriptor>,
      state: PluginEnabledState,
      project: Project? = null,
      parentComponent: JComponent? = null,
    ): Boolean {
      if (!state.isPerProject) {
        DisabledPluginsState.enablePlugins(
          descriptors,
          state.isEnabled,
          false,
        )
      }

      return if (state.isEnabled)
        loadPlugins(descriptors)
      else
        unloadPlugins(descriptors, project, parentComponent)
    }

    @JvmStatic
    internal fun loadPlugins(descriptors: Collection<IdeaPluginDescriptor>): Boolean {
      /*
      model: enabled (descriptor, disabledList, load), enabled per project (descriptor, load)
      project opening: enabled per project (descriptor, load)
      project closing: disabled per project (descriptor, load iff not is disabled globally)

      P1, plugin is disabled per project
      try to open P2 (new window)
      how to enable the plugin?
      */

      return descriptors.isEmpty() ||
             DynamicPlugins.loadPlugins(descriptors).requireRestartIfNecessary()
    }

    @JvmStatic
    @JvmOverloads
    internal fun unloadPlugins(
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

    class ProjectPluginTrackerManagerState : BaseState() {

      @get:XCollection
      var trackers by map<String, ProjectPluginTracker.Companion.ProjectPluginTrackerState>()
    }

    private fun shouldUnload(project: Project?): (IdeaPluginDescriptor) -> Boolean {
      val managers = ProjectManager
        .getInstance()
        .openProjects
        .filterNot { it == project }
        .mapNotNull { createPluginTrackerOrNull(it) }
        .toList()

      return { descriptor ->
        val pluginId = descriptor.pluginId
        managers.all { !it.isEnabled(pluginId) } &&
        (DisabledPluginsState.disabledPlugins().contains(pluginId) || managers.all { it.isDisabled(pluginId) })
      }
    }

    private fun Boolean.requireRestartIfNecessary(): Boolean {
      if (!this) {
        InstalledPluginsState.getInstance().isRestartRequired = true
      }
      return this
    }
  }

  private var applicationShuttingDown = false

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(
      ProjectManager.TOPIC,
      object : ProjectManagerListener {
        override fun projectClosing(project: Project) {
          if (applicationShuttingDown) return
          createPluginTracker(project).loadUnloadPlugins(false)
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
      state.trackers.getOrPut(key) { ProjectPluginTracker.Companion.ProjectPluginTrackerState() }
    )
  }
}
