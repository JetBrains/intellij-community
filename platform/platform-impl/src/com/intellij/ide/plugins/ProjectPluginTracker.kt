// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener

@Service
@State(
  name = "ProjectPluginTracker",
  storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class ProjectPluginTracker(private val project: Project) : PersistentStateComponent<ProjectPluginTracker.Companion.State> {

  companion object {

    internal val LOG = logger<ProjectPluginTracker>()

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
  }

  private var state = State()
  private var applicationShuttingDown = false

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {

      override fun projectOpened(project: Project) {
        if (isNotTargetProject(project)) return

        updatePluginEnabledState(
          project,
          "load",
          getEnabledPlugins(),
          getDisabledPlugins()
        )
      }

      override fun projectClosing(project: Project) {
        if (applicationShuttingDown ||
            isNotTargetProject(project)) return

        updatePluginEnabledState(
          project,
          "unload",
          getDisabledPlugins(),
          getEnabledPlugins()
        )
      }

      private fun isNotTargetProject(project: Project) =
        this@ProjectPluginTracker.project.name != project.name

      private fun updatePluginEnabledState(project: Project,
                                           projectState: String,
                                           pluginsToEnable: List<IdeaPluginDescriptor>,
                                           pluginsToDisable: List<IdeaPluginDescriptor>) {
        LOG.info("""|Enabling plugins on project $projectState: ${pluginsToEnable.joinIds()}
                    |Disabling plugins on project $projectState: ${pluginsToDisable.joinIds()}
                 """.trimMargin())

        PluginEnabler.updatePluginEnabledState(
          project,
          pluginsToEnable,
          pluginsToDisable,
          null
        )
      }
    })

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

private fun List<IdeaPluginDescriptor>.joinIds() =
  joinToString { it.pluginId.idString }

private fun Set<String>.containsPluginId(descriptor: IdeaPluginDescriptor) =
  contains(descriptor.pluginId.idString)

private fun Set<String>.findPluginById() =
  mapNotNull { PluginId.findId(it) }
    .mapNotNull { PluginManagerCore.getPlugin(it) }