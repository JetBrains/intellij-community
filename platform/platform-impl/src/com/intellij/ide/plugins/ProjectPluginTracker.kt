// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.startup.StartupManager

@Service
@State(
  name = "ProjectPluginTracker",
  storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class ProjectPluginTracker : PersistentStateComponent<ProjectPluginTracker.Companion.State> {

  companion object {

    @JvmStatic
    fun getInstance(project: Project): ProjectPluginTracker =
      project.getService(ProjectPluginTracker::class.java)

    class State {

      var enabledPlugins = mutableSetOf<String>()
      var disabledPlugins = mutableSetOf<String>()

      fun register(id: PluginId,
                   enable: Boolean) {
        val setToRemoveFrom = if (enable) disabledPlugins else enabledPlugins
        val setToAddTo = if (enable) enabledPlugins else disabledPlugins

        val idString = id.idString
        if (!setToRemoveFrom.remove(idString)) {
          setToAddTo.add(idString)
        }
      }

      fun unregister(id: PluginId) {
        val idString = id.idString
        if (!enabledPlugins.remove(idString)) {
          disabledPlugins.remove(idString)
        }
      }
    }

    class EnableDisablePluginsActivity : StartupActivity {

      init {
        if (ApplicationManager.getApplication().isUnitTestMode) {
          throw ExtensionNotApplicableException.INSTANCE
        }
      }

      /**
       * Triggers [ProjectPluginTracker.loadState].
       *
       * @param project a project to enable/disable plugins for
       */
      override fun runActivity(project: Project) {
        StartupManager.getInstance(project).runAfterOpened {
          EnableDisablePluginsListener.projectOpened(project)
        }
      }
    }

    private object EnableDisablePluginsListener : ProjectManagerListener {

      override fun projectOpened(project: Project) {
        val pluginTracker = getInstance(project)

        PluginEnabler.updatePluginEnabledState(
          project,
          pluginTracker.getEnabledPlugins(),
          pluginTracker.getDisabledPlugins(),
          null
        )
      }

      override fun projectClosing(project: Project) {
        val pluginTracker = getInstance(project)
        if (pluginTracker.applicationShuttingDown) return

        PluginEnabler.updatePluginEnabledState(
          project,
          pluginTracker.getDisabledPlugins(),
          pluginTracker.getEnabledPlugins(),
          null
        )
      }
    }
  }

  private var state = State()
  private var applicationShuttingDown = false

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(ProjectManager.TOPIC, EnableDisablePluginsListener)

    connection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        applicationShuttingDown = true
      }
    })
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

  private fun getEnabledPlugins() = state
    .enabledPlugins
    .findPluginById()

  private fun getDisabledPlugins() = state
    .disabledPlugins
    .findPluginById()
}

private fun Set<String>.containsPluginId(descriptor: IdeaPluginDescriptor) =
  contains(descriptor.pluginId.idString)

private fun Set<String>.findPluginById() =
  mapNotNull { PluginId.findId(it) }
    .mapNotNull { PluginManagerCore.getPlugin(it) }