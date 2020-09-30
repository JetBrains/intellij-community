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

      var projectPluginReferences = mutableSetOf<String>()

      fun getEnabledPlugins() = projectPluginReferences
        .mapNotNull { PluginId.findId(it) }

      fun isEnabled(id: PluginId) =
        projectPluginReferences.contains(id.idString)

      operator fun plus(id: PluginId): State {
        projectPluginReferences.add(id.idString)
        return this
      }

      operator fun minus(id: PluginId): State {
        projectPluginReferences.remove(id.idString)
        return this
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

        PluginEnabler.enablePlugins(
          project,
          getEnabledPlugins(),
          true
        )
      }

      override fun projectClosing(project: Project) {
        if (applicationShuttingDown ||
            isNotTargetProject(project)) return

        val pluginDescriptorsToUnload = getEnabledPlugins()

        // todo move to PluginEnabler.enablePlugins
        LOG.info("Disabling plugins on project unload: " + pluginDescriptorsToUnload.joinToString { it.pluginId.toString() })
        PluginEnabler.enablePlugins(
          project,
          pluginDescriptorsToUnload,
          false
        )
      }

      private fun isNotTargetProject(project: Project) =
        this@ProjectPluginTracker.project.name != project.name
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

  fun registerProjectPlugin(plugin: IdeaPluginDescriptor) {
    state += plugin.pluginId
  }

  fun unregisterProjectPlugin(plugin: IdeaPluginDescriptor) {
    state -= plugin.pluginId
  }

  fun getEnabledPlugins(): List<IdeaPluginDescriptor> = state
    .getEnabledPlugins()
    .mapNotNull { PluginManagerCore.getPlugin(it) }

  fun isRegistered(plugin: IdeaPluginDescriptor) =
    state.isEnabled(plugin.pluginId)
}
