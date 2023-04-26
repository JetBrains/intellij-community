// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectsGroupItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectTreeItem

open class RecentProjectListActionProvider {
  companion object {
    @JvmStatic
    fun getInstance() = service<RecentProjectListActionProvider>()
  }

  internal fun collectProjectsWithoutCurrent(currentProject: Project): List<RecentProjectTreeItem> = collectProjects(currentProject)

  internal fun collectProjects(): List<RecentProjectTreeItem> = collectProjects(projectToFilterOut = null)

  private fun collectProjects(projectToFilterOut: Project?): List<RecentProjectTreeItem> {
    val recentProjectManager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase
    val openedPaths = ProjectManagerEx.getOpenProjects().mapNotNullTo(LinkedHashSet(), recentProjectManager::getProjectPath)
    val allRecentProjectPaths = LinkedHashSet(recentProjectManager.getRecentPaths())
    if (projectToFilterOut != null) {
      allRecentProjectPaths.remove(recentProjectManager.getProjectPath(projectToFilterOut))
    }

    val duplicates = getDuplicateProjectNames(openedPaths, allRecentProjectPaths, recentProjectManager)
    val groups = recentProjectManager.groups.sortedWith(ProjectGroupComparator(allRecentProjectPaths))
    val projectGroups = groups.asSequence().map { projectGroup ->
      val projects = projectGroup.projects.toSet()
      val children = projects.map { recentProject ->
        createRecentProject(recentProject, duplicates, projectGroup)
      }
      for (project in projects) {
        allRecentProjectPaths.remove(project)
      }
      return@map ProjectsGroupItem(projectGroup, children)
    }

    val projectsWithoutGroups = allRecentProjectPaths.asSequence().map { recentProject ->
      createRecentProject(recentProject, duplicates, null)
    }
    return (projectGroups + projectsWithoutGroups).toList()
  }

  @JvmOverloads
  open fun getActions(addClearListItem: Boolean = false, useGroups: Boolean = false): List<AnAction> {
    val recentProjectManager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase
    val paths = LinkedHashSet(recentProjectManager.getRecentPaths())
    val openedPaths = LinkedHashSet<String>()
    for (openProject in ProjectUtilCore.getOpenProjects()) {
      recentProjectManager.getProjectPath(openProject)?.let {
        openedPaths.add(it)
      }
    }

    val actions = mutableListOf<AnAction>()
    val duplicates = getDuplicateProjectNames(openedPaths, paths, recentProjectManager)
    val groups = recentProjectManager.groups.toMutableList()
    if (useGroups) {
      groups.sortWith(ProjectGroupComparator(paths))

      for (group in groups) {
        for (project in group.projects) {
          paths.remove(project)
        }
      }

      addGroups(groups = groups, duplicates = duplicates, addClearListItem = addClearListItem, actions = actions, bottom = false)
    }

    for (path in paths) {
      actions.add(createOpenAction(path, duplicates))
    }

    if (useGroups) {
      addGroups(groups, duplicates, addClearListItem, actions, true)
    }
    return actions
  }

  private fun addGroups(groups: List<ProjectGroup>,
                        duplicates: Set<String>,
                        addClearListItem: Boolean,
                        actions: MutableList<AnAction>,
                        bottom: Boolean) {
    for (group in groups.asSequence().filter { it.isBottomGroup == bottom }) {
      val children = mutableListOf<AnAction>()
      for (path in group.projects) {
        val action = createOpenAction(path ?: continue, duplicates)
        action.setProjectGroup(group)
        children.add(action)
        if (addClearListItem && children.size >= RecentProjectsManagerBase.MAX_PROJECTS_IN_MAIN_MENU) {
          break
        }
      }
      actions.add(ProjectGroupActionGroup(group, children))
      if (group.isExpanded) {
        actions.addAll(children)
      }
    }
  }

  // for Rider
  protected open fun createOpenAction(path: String, duplicates: Set<String>): ReopenProjectAction {
    val recentProjectManager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase
    var displayName = recentProjectManager.getDisplayName(path)
    val projectName = recentProjectManager.getProjectName(path)

    if (displayName.isNullOrBlank()) {
      displayName = if (duplicates.contains(projectName)) FileUtil.toSystemDependentName(path) else projectName
    }

    // It's better don't to remove non-existent projects. Sometimes projects stored
    // on USB-sticks or flash-cards, and it will be nice to have them in the list
    // when USB device or SD-card is mounted
    return ReopenProjectAction(path, projectName, displayName)
  }

  private fun createRecentProject(path: String, duplicates: Set<String>, projectGroup: ProjectGroup?): RecentProjectItem {
    val reopenProjectAction = createOpenAction(path, duplicates)
    return RecentProjectItem(
      projectPath = reopenProjectAction.projectPath,
      projectName = reopenProjectAction.projectName,
      displayName = reopenProjectAction.projectNameToDisplay ?: "",
      projectGroup = projectGroup
    )
  }

  /**
   * Returns true if action corresponds to specified project
   */
  open fun isCurrentProjectAction(project: Project, action: ReopenProjectAction): Boolean = action.projectPath == project.basePath
}

private fun getDuplicateProjectNames(openedPaths: Set<String>,
                                     recentPaths: Set<String>,
                                     recentProjectManager: RecentProjectsManagerBase): Set<String> {
  val names = HashSet<String>()
  val duplicates = HashSet<String>()
  // a project name should not be considered duplicate if a project is both in recent projects and open projects (IDEA-211955)
  for (path in (openedPaths + recentPaths)) {
    val name = recentProjectManager.getProjectName(path)
    if (!names.add(name)) {
      duplicates.add(name)
    }
  }
  return duplicates
}

private class ProjectGroupComparator(private val projectPaths: Set<String>) : Comparator<ProjectGroup> {
  override fun compare(o1: ProjectGroup, o2: ProjectGroup): Int {
    val ind1 = getGroupIndex(o1)
    val ind2 = getGroupIndex(o2)
    return if (ind1 == ind2) NaturalComparator.INSTANCE.compare(o1.name, o2.name) else ind1 - ind2
  }

  private fun getGroupIndex(group: ProjectGroup): Int {
    var index = Integer.MAX_VALUE
    for (path in group.projects) {
      val i = projectPaths.indexOf(path)
      if (i in 0 until index) {
        index = i
      }
    }
    return index
  }
}