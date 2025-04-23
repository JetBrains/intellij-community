// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.wm.impl.headertoolbar.ProjectToolbarWidgetPresentable
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectsGroupItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProviderRecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectTreeItem
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

open class RecentProjectListActionProvider {
  companion object {
    @JvmStatic
    fun getInstance(): RecentProjectListActionProvider = service<RecentProjectListActionProvider>()

    private val EP = ExtensionPointName.create<RecentProjectProvider>("com.intellij.recentProjectsProvider")
  }

  internal fun collectProjectsWithoutCurrent(currentProject: Project): List<RecentProjectTreeItem> = collectProjects(currentProject)

  @ApiStatus.Internal
  fun collectProjects(): List<RecentProjectTreeItem> = collectProjects(projectToFilterOut = null)

  private fun collectProjects(projectToFilterOut: Project?): List<RecentProjectTreeItem> {
    val recentProjectManager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase
    val openedPaths = ProjectManagerEx.getOpenProjects().mapNotNullTo(LinkedHashSet(), recentProjectManager::getProjectPath)
    val allRecentProjectPaths = LinkedHashSet(recentProjectManager.getRecentPaths())
    if (projectToFilterOut != null) {
      allRecentProjectPaths.remove(recentProjectManager.getProjectPath(projectToFilterOut))
    }

    val duplicates = getDuplicateProjectNames(openedPaths, allRecentProjectPaths, recentProjectManager)
    val groups = recentProjectManager.groups.sortedWith(ProjectGroupComparator(allRecentProjectPaths))
    val projectGroups = groups.map { projectGroup ->
      val projects = projectGroup.projects.toSet()
      val children = projects.map { recentProject ->
        createRecentProject(
          path = recentProject,
          duplicates = duplicates,
          projectGroup = projectGroup,
          recentProjectManager = recentProjectManager,
        )
      }
      for (project in projects) {
        allRecentProjectPaths.remove(project)
      }
      return@map ProjectsGroupItem(projectGroup, children)
    }

    val projectsWithoutGroups = allRecentProjectPaths.map { recentProject ->
      createRecentProject(path = recentProject, duplicates = duplicates, projectGroup = null, recentProjectManager = recentProjectManager)
    }

    val projectsFromEP = if (Registry.`is`("ide.recent.projects.query.ep.providers"))
      EP.extensionList.flatMap { createProjectsFromProvider(it) }
    else emptyList()

    val mergedProjectsWithoutGroups = insertProjectsFromProvider(projectsWithoutGroups.toList(), projectsFromEP) { it.activationTimestamp }
    return (projectGroups + mergedProjectsWithoutGroups).toList()
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

    val duplicates = getDuplicateProjectNames(openedPaths, paths, recentProjectManager)
    val groups = recentProjectManager.groups.toMutableList()

    val topGroups = if (useGroups) {
      groups.sortWith(ProjectGroupComparator(paths))

      for (group in groups) {
        for (project in group.projects) {
          paths.remove(project)
        }
      }

      addGroups(
        groups = groups,
        duplicates = duplicates,
        addClearListItem = addClearListItem,
        bottom = false,
        recentProjectManager = recentProjectManager,
      )
    }
    else {
      emptyList()
    }

    val actionsWithoutGroup = mutableListOf<AnAction>()
    for (path in paths) {
      actionsWithoutGroup.add(createOpenAction(path, duplicates, recentProjectManager))
    }

    val bottomGroups = if (useGroups) {
      addGroups(
        groups = groups,
        duplicates = duplicates,
        addClearListItem = addClearListItem,
        bottom = true,
        recentProjectManager = recentProjectManager,
      )
    }
    else {
      emptyList()
    }

    val actionsFromEP = if (Registry.`is`("ide.recent.projects.query.ep.providers")) {
      EP.extensionList.flatMap { createActionsFromProvider(it) }
    }
    else emptyList()

    val mergedProjectsWithoutGroups = insertProjectsFromProvider(actionsWithoutGroup, actionsFromEP) { it.activationTimestamp }
    return (topGroups + mergedProjectsWithoutGroups + bottomGroups)
  }

  private fun addGroups(
    groups: List<ProjectGroup>,
    duplicates: Set<ProjectNameOrPathIfNotYetComputed>,
    addClearListItem: Boolean,
    bottom: Boolean,
    recentProjectManager: RecentProjectsManagerBase,
  ): List<AnAction> {
    val actions = mutableListOf<AnAction>()
    for (group in groups.filter { it.isBottomGroup == bottom }) {
      val children = mutableListOf<AnAction>()
      for (path in group.projects) {
        val action = createOpenAction(path = path ?: continue, duplicates = duplicates, recentProjectManager = recentProjectManager)
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
    return actions
  }

  protected open fun createOpenAction(
    path: String,
    duplicates: Set<ProjectNameOrPathIfNotYetComputed>,
    recentProjectManager: RecentProjectsManagerBase,
  ): ReopenProjectAction {
    var displayName = recentProjectManager.getDisplayName(path)
    val projectName = recentProjectManager.getProjectName(path)
    val activationTimestamp = recentProjectManager.getActivationTimestamp(path)

    var branch: String? = null

    if (displayName.isNullOrBlank()) {
      displayName = if (duplicates.contains(ProjectNameOrPathIfNotYetComputed(projectName))) {
        if (Registry.`is`("ide.welcome.screen.branch.name", true)) {
          branch = recentProjectManager.getCurrentBranchName(path)
        }
        FileUtil.toSystemDependentName(path)
      }
      else {
        projectName
      }
    }

    // It's better don't to remove non-existent projects.
    // Sometimes projects are stored on USB-sticks or flash-cards, and it will be nice to have them in the list
    // when a USB device or SD-card is mounted
    return ReopenProjectAction(projectPath = path, projectName = projectName, displayName = displayName, branchName = branch,
                               activationTimestamp = activationTimestamp)
  }

  private fun createRecentProject(
    path: String,
    duplicates: Set<ProjectNameOrPathIfNotYetComputed>,
    projectGroup: ProjectGroup?,
    recentProjectManager: RecentProjectsManagerBase,
  ): RecentProjectItem {
    val reopenProjectAction = createOpenAction(path = path, duplicates = duplicates, recentProjectManager = recentProjectManager)
    return RecentProjectItem(
      projectPath = reopenProjectAction.projectPath,
      projectName = reopenProjectAction.projectName ?: "",
      displayName = reopenProjectAction.projectNameToDisplay,
      branchName = reopenProjectAction.branchName,
      activationTimestamp = reopenProjectAction.activationTimestamp,
      projectGroup = projectGroup,
    )
  }

  private fun createProjectsFromProvider(provider: RecentProjectProvider): List<ProviderRecentProjectItem> {
    return provider.getRecentProjects().map { project ->
      val projectId = getProviderProjectId(provider, project)
      ProviderRecentProjectItem(projectId, project)
    }
  }

  private fun createActionsFromProvider(provider: RecentProjectProvider): List<AnAction> {
    return provider.getRecentProjects().map { project ->
      val projectId = getProviderProjectId(provider, project)
      RemoteRecentProjectAction(projectId, project)
    }
  }

  /**
   * Keep [projects] order intact, but insert [projectsFromEP] into the correct place if possible
   */
  private fun <T> insertProjectsFromProvider(
    projects: List<T>,
    projectsFromEP: List<T>,
    timestampGetter: (T) -> Long?,
  ): List<T> {
    if (projectsFromEP.isEmpty()) return projects

    fun List<T>.indexOfFirstOrSize(predicate: (T) -> Boolean): Int {
      val index = indexOfFirst(predicate)
      return if (index == -1) size else index
    }

    val cutIndex = projects.indexOfFirstOrSize { timestampGetter(it) == null }
    val mergedPrefix = projects.subList(0, cutIndex).toMutableList()
    val mergedSuffix = projects.subList(cutIndex, projects.size).toMutableList()

    for (projectFromEP in projectsFromEP) {
      val projectFromEPTimestamp = timestampGetter(projectFromEP)
      if (projectFromEPTimestamp == null) {
        mergedSuffix.add(projectFromEP)
      }
      else {
        val insertIndex = mergedPrefix.indexOfFirstOrSize { item ->
          val timestamp = timestampGetter(item) ?: 0L
          return@indexOfFirstOrSize timestamp < projectFromEPTimestamp
        }
        mergedPrefix.add(index = insertIndex, element = projectFromEP)
      }
    }
    return mergedPrefix + mergedSuffix
  }

  /**
   * Returns true if action corresponds to a specified project
   */
  open fun isCurrentProjectAction(project: Project, action: ReopenProjectAction): Boolean = action.projectPath == project.basePath
}

private fun getDuplicateProjectNames(
  openedPaths: Set<String>,
  recentPaths: Set<String>,
  recentProjectManager: RecentProjectsManagerBase,
): Set<ProjectNameOrPathIfNotYetComputed> {
  val names = HashSet<ProjectNameOrPathIfNotYetComputed>()
  val duplicates = HashSet<ProjectNameOrPathIfNotYetComputed>()
  // a project name should not be considered duplicate if a project is both in recent projects and open projects (IDEA-211955)
  for (path in (openedPaths + recentPaths)) {
    val name = ProjectNameOrPathIfNotYetComputed(recentProjectManager.getProjectName(path))
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

private class RemoteRecentProjectAction(val projectId: String, val project: RecentProject) : DumbAwareAction(), ProjectToolbarWidgetPresentable {
  init {
    var text = project.displayName
    if (project.providerName != null) text += " [${project.providerName}]"
    if (project.branchName != null) text += " [${project.branchName}]"
    templatePresentation.text = text
  }

  override fun actionPerformed(e: AnActionEvent) {
    project.openProject()
  }

  override val projectNameToDisplay: @NlsSafe String = project.displayName
  override val providerPathToDisplay: @NlsSafe String? get() = project.providerPath
  override val projectPathToDisplay: @NlsSafe String? = project.projectPath
  override val branchName: @NlsSafe String? = project.branchName
  override val projectIcon: Icon
    get() = project.icon
            ?: RecentProjectsManagerBase.getInstanceEx().getNonLocalProjectIcon(projectId, true, unscaledProjectIconSize(), project.displayName)
  override val providerIcon: Icon? get() = project.providerIcon
  override val activationTimestamp: Long? get() = project.activationTimestamp
}

private fun getProviderProjectId(provider: RecentProjectProvider, project: RecentProject): String {
  return provider.providerId + "$" + (project.projectId ?: project.displayName)
}

private val RecentProjectTreeItem.activationTimestamp
  get() = when (this) {
    is RecentProjectItem -> activationTimestamp
    is ProviderRecentProjectItem -> activationTimestamp
    else -> null
  }

private val AnAction.activationTimestamp
  get() = when (this) {
    is ProjectToolbarWidgetPresentable -> activationTimestamp
    else -> null
  }

