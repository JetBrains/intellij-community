// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity

@Service
@State(
  name = "ProjectPluginTracker",
  storages = [Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)]
)
internal class ProjectPluginTracker(project: Project) : PersistentStateComponent<ProjectPluginTracker.Companion.State> {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectPluginTracker = project.service()

    class State {
      var enabledPlugins = mutableSetOf<String>()
      var disabledPlugins = mutableSetOf<String>()

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
          null
        )
      }

      private fun setToAddTo(enable: Boolean) = if (enable) enabledPlugins else disabledPlugins

      private fun setToRemoveFrom(enable: Boolean) = if (enable) disabledPlugins else enabledPlugins
    }

    internal class EnableDisablePluginsActivity : StartupActivity.RequiredForSmartMode {

      init {
        if (getApplication().isUnitTestMode) {
          throw ExtensionNotApplicableException.INSTANCE
        }
      }

      /**
       * Triggers [ProjectPluginTracker.loadState].
       *
       * @param project a project to enable/disable plugins for
       */
      override fun runActivity(project: Project) = updatePluginEnabledState(project, true)
    }

    private fun updatePluginEnabledState(project: Project, enable: Boolean) {
      val pluginTracker = getInstance(project)
      if (pluginTracker.applicationShuttingDown) return

      pluginTracker.state.updatePluginEnabledState(project, enable)
    }
  }

  private var state = State()
  private var applicationShuttingDown = false

  init {
    val connection = getApplication().messageBus.connect(project)
    connection.subscribe(
      ProjectManager.TOPIC,
      object : ProjectManagerListener {
        override fun projectClosing(project: Project) = updatePluginEnabledState(project, false)
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

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }

  fun changeEnableDisable(plugin: IdeaPluginDescriptor,
                          newState: PluginEnabledState) {
    val pluginId = plugin.pluginId
    if (newState.isPerProject) {
      state.register(pluginId, enable = newState.isEnabled)
    }
    else {
      state.unregister(pluginId)
    }
  }

  fun isEnabled(plugin: IdeaPluginDescriptor) = state
    .enabledPlugins
    .containsPluginId(plugin)

  fun isDisabled(plugin: IdeaPluginDescriptor) = state
    .disabledPlugins
    .containsPluginId(plugin)
}

private fun Set<String>.containsPluginId(descriptor: IdeaPluginDescriptor) = contains(descriptor.pluginId.idString)

private fun Set<String>.findPluginById() = mapNotNull { PluginId.findId(it) }.mapNotNull { PluginManagerCore.getPlugin(it) }