// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path

interface RecentProjectsManager {
  companion object {
    @Topic.AppLevel
    val RECENT_PROJECTS_CHANGE_TOPIC: Topic<RecentProjectsChange> = Topic(RecentProjectsChange::class.java, Topic.BroadcastDirection.NONE)

    @Topic.AppLevel
    val LAST_PROJECTS_TOPIC: Topic<LastProjectsListener> = Topic(LastProjectsListener::class.java, Topic.BroadcastDirection.NONE)

    @JvmStatic
    @RequiresBlockingContext
    fun getInstance(): RecentProjectsManager = ApplicationManager.getApplication().service<RecentProjectsManager>()
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

  val groups: List<ProjectGroup>
    get() = emptyList()

  fun addGroup(group: ProjectGroup) {}

  fun removeGroup(group: ProjectGroup) {}

  fun moveProjectToGroup(projectPath: String, to: ProjectGroup) {}

  fun removeProjectFromGroup(projectPath: String, from: ProjectGroup) {}

  fun hasPath(path: @SystemIndependent String?): Boolean = false

  suspend fun willReopenProjectOnStart(): Boolean = false

  @ApiStatus.Internal
  suspend fun reopenLastProjectsOnStart(): Boolean

  @ApiStatus.Internal
  fun setActivationTimestamp(project: Project, timestamp: Long)

  fun suggestNewProjectLocation(): String

  // Change of recent projects
  interface RecentProjectsChange {
    @RequiresEdt
    fun change()
  }

  /**
   * Allows observing events related to the last projects that were open when exiting the IDE
   *
   * @see LAST_PROJECTS_TOPIC
   */
  interface LastProjectsListener {
    /**
     * Called after reopening the last projects
     */
    @RequiresEdt
    fun lastProjectsReopened(activeProject: Project)
  }
}