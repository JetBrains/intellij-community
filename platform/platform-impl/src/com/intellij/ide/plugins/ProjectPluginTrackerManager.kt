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

@Service
@State(
  name = "ProjectPluginTrackerManager",
  storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)],
)
class ProjectPluginTrackerManager : SimplePersistentStateComponent<ProjectPluginTrackerManager.Companion.ProjectPluginTrackerManagerState>(
  ProjectPluginTrackerManagerState()) {

  companion object {

    @JvmStatic
    fun getInstance() = service<ProjectPluginTrackerManager>()

    @JvmStatic
    fun createPluginTrackerOrNull(project: Project?): ProjectPluginTracker? = project?.let { getInstance().createPluginTracker(it) }

    class ProjectPluginTrackerManagerState : BaseState() {

      @get:XCollection
      var trackers by map<String, ProjectPluginTracker.Companion.ProjectPluginTrackerState>()
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
          createPluginTracker(project).updatePluginEnabledState(false)
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
