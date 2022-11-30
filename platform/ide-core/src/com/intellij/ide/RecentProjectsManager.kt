// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path

interface RecentProjectsManager {
  companion object {
    @Topic.AppLevel
    val RECENT_PROJECTS_CHANGE_TOPIC = Topic("Change of recent projects", RecentProjectsChange::class.java, Topic.BroadcastDirection.NONE)

    @JvmStatic
    fun getInstance(): RecentProjectsManager = ApplicationManager.getApplication().getService(RecentProjectsManager::class.java)

    fun fireChangeEvent() {
      ApplicationManager.getApplication().messageBus.syncPublisher(RECENT_PROJECTS_CHANGE_TOPIC).change()
    }
  }

  // a path pointing to a directory where the last project was created or null if not available
  var lastProjectCreationLocation: @SystemIndependent String?

  fun setLastProjectCreationLocation(value: Path?) {
    lastProjectCreationLocation = if (value == null) {
      null
    }
    else {
      PathUtil.toSystemIndependentName(value.toString())
    }
  }

  fun updateLastProjectPath()

  fun removePath(path: @SystemIndependent String)

  @Deprecated("Use {@link RecentProjectListActionProvider#getActions}")
  fun getRecentProjectsActions(addClearListItem: Boolean): Array<AnAction>

  @Deprecated("Use {@link RecentProjectListActionProvider#getActions}")
  fun getRecentProjectsActions(addClearListItem: Boolean, useGroups: Boolean): Array<AnAction> {
    return getRecentProjectsActions(addClearListItem)
  }

  val groups: List<ProjectGroup>
    get() = emptyList()

  fun addGroup(group: ProjectGroup) {}

  fun removeGroup(group: ProjectGroup) {}

  fun moveProjectToGroup(projectPath: String, to: ProjectGroup) {}

  fun removeProjectFromGroup(projectPath: String, from: ProjectGroup) {}

  fun hasPath(path: @SystemIndependent String?): Boolean {
    return false
  }

  fun willReopenProjectOnStart(): Boolean

  @ApiStatus.Internal
  suspend fun reopenLastProjectsOnStart(): Boolean

  @ApiStatus.Internal
  fun setActivationTimestamp(project: Project, timestamp: Long)

  fun suggestNewProjectLocation(): String

  interface RecentProjectsChange {
    fun change()
  }
}