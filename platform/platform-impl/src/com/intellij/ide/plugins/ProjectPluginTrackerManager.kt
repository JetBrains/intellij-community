// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.ProjectPluginTracker.Companion.ProjectPluginTrackerState
import com.intellij.ide.plugins.ProjectPluginTrackerManager.Companion.ProjectPluginTrackerManagerState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.project.stateStore
import com.intellij.util.xmlb.annotations.XCollection

@Service
@State(
  name = "ProjectPluginTrackerManager",
  storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)],
)
internal class ProjectPluginTrackerManager : SimplePersistentStateComponent<ProjectPluginTrackerManagerState>(
  ProjectPluginTrackerManagerState()) {

  companion object {

    @JvmStatic
    fun createPluginTrackerOrNull(project: Project?): ProjectPluginTracker? = project?.let { createPluginTracker(it) }

    @JvmStatic
    fun createPluginTracker(project: Project): ProjectPluginTracker {
      return service<ProjectPluginTrackerManager>()
        .createPluginTrackerImpl(project)
    }

    internal class ProjectPluginTrackerManagerState : BaseState() {

      @get:XCollection
      var trackers by map<String, ProjectPluginTrackerState>()
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
          createPluginTrackerImpl(project).updatePluginEnabledState(false)
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

  private fun createPluginTrackerImpl(project: Project): ProjectPluginTracker {
    val key = project.stateStore.projectWorkspaceId ?: project.name
    return ProjectPluginTracker(
      project,
      state.trackers.getOrPut(key) { ProjectPluginTrackerState() }
    )
  }
}