// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.IdeDependentActionGroup
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.platform.getMultiProjectDisplayName
import com.intellij.project.ProjectStoreOwner
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.File
import java.nio.file.Path
import java.util.Arrays

@Internal
class ProjectWindowActionGroup : IdeDependentActionGroup(), ActionRemoteBehaviorSpecification.Frontend {
  private var latest: ProjectWindowAction? = null

  internal fun addProject(project: Project) {
    if (project !is ProjectStoreOwner) {
      return
    }

    val projectLocation = project.componentStore.storeDescriptor.presentableUrl
    val projectName = getProjectDisplayName(project)
    val windowAction = ProjectWindowAction(
      projectName = projectName,
      projectLocation = projectLocation,
      previous = latest,
      excludedFromProjectWindowSwitchOrder = service<ProjectFrameCapabilitiesService>().has(
        project,
        ProjectFrameCapability.EXCLUDE_FROM_PROJECT_WINDOW_SWITCH_ORDER,
      ),
    )
    val duplicateWindowActions = findWindowActionsWithProjectName(projectName)
    if (!duplicateWindowActions.isEmpty()) {
      for (action in duplicateWindowActions) {
        action.getTemplatePresentation().setText(getLocationRelativeToUserHome(action.projectLocation))
      }
      windowAction.getTemplatePresentation().setText(getLocationRelativeToUserHome(windowAction.projectLocation))
    }
    add(windowAction)
    latest = windowAction
  }

  fun removeProject(project: Project) {
    val storeDescriptor = (project as? ProjectStoreOwner ?: return).componentStore.storeDescriptor
    val windowAction = findWindowAction(storeDescriptor.presentableUrl) ?: return
    if (latest == windowAction) {
      val previous = latest?.previous
      latest = if (previous == latest) null else previous
    }
    remove(windowAction)
    val projectName = getProjectDisplayName(project)
    val duplicateWindowActions = findWindowActionsWithProjectName(projectName)
    if (duplicateWindowActions.size == 1) {
      duplicateWindowActions.first().getTemplatePresentation().setText(projectName)
    }
    windowAction.dispose()
  }

  fun canActivateNext(project: Project?): Boolean {
    val projectLocation = (project as? ProjectStoreOwner)?.componentStore?.storeDescriptor?.presentableUrl ?: return false
    return findSwitchTarget(projectLocation, next = true) != null
  }

  fun canActivatePrevious(project: Project?): Boolean {
    val projectLocation = (project as? ProjectStoreOwner)?.componentStore?.storeDescriptor?.presentableUrl ?: return false
    return findSwitchTarget(projectLocation, next = false) != null
  }

  override fun isDumbAware(): Boolean = true

  fun activateNextWindow(e: AnActionEvent) {
    val presentableUrl = (e.getData(CommonDataKeys.PROJECT) as? ProjectStoreOwner ?: return).componentStore.storeDescriptor.presentableUrl
    findSwitchTarget(presentableUrl, next = true)?.setSelected(e, true)
  }

  fun activatePreviousWindow(e: AnActionEvent) {
    val presentableUrl = (e.getData(CommonDataKeys.PROJECT) as? ProjectStoreOwner ?: return).componentStore.storeDescriptor.presentableUrl
    findSwitchTarget(presentableUrl, next = false)?.setSelected(e, true)
  }

  fun findSwitchTarget(projectLocation: Path, next: Boolean): ProjectWindowAction? {
    val windowAction = findWindowAction(projectLocation) ?: return null
    var candidate = if (next) windowAction.next else windowAction.previous
    while (candidate != null && candidate != windowAction) {
      if (!candidate.excludedFromProjectWindowSwitchOrder) {
        return candidate
      }
      candidate = if (next) candidate.next else candidate.previous
    }
    return null
  }

  private fun findWindowAction(projectLocation: Path): ProjectWindowAction? {
    val children = getChildren(ActionManager.getInstance())
    for (child in children) {
      if (child !is ProjectWindowAction) {
        continue
      }
      if (projectLocation == child.projectLocation) {
        return child
      }
    }
    return null
  }

  private fun findWindowActionsWithProjectName(projectName: String): List<ProjectWindowAction> {
    var result: MutableList<ProjectWindowAction>? = null
    val children = getChildren(ActionManager.getInstance())
    for (child in children) {
      if (child !is ProjectWindowAction) {
        continue
      }

      if (projectName == child.projectName) {
        if (result == null) {
          result = ArrayList<ProjectWindowAction>()
        }
        result.add(child)
      }
    }
    return result ?: emptyList()
  }

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    val children = super.getChildren(event)
    Arrays.sort(children, SORT_BY_NAME)
    if (event != null) {
      return children.filterNot(::shouldHideInProjectWindowsList).toTypedArray()
    }
    return children
  }
}

private fun shouldHideInProjectWindowsList(action: AnAction): Boolean {
  return action is ProjectWindowAction && action.excludedFromProjectWindowSwitchOrder
}

@NlsActions.ActionText
private fun getProjectDisplayName(project: Project): @NlsActions.ActionText String {
  if (LightEdit.owns(project)) {
    return LightEditService.windowName
  }

  return getMultiProjectDisplayName(project) ?: project.getName()
}

private fun getProjectName(action: AnAction?): String? {
  return if (action is ProjectWindowAction) action.projectName else null
}

private val SORT_BY_NAME = Comparator { action1: AnAction?, action2: AnAction? ->
  NaturalComparator.INSTANCE.compare(getProjectName(action1), getProjectName(action2))
}

private fun getLocationRelativeToUserHome(file: Path): @NlsSafe String {
  val userHomeDir = Path.of(SystemProperties.getUserHome())
  if (file.startsWith(userHomeDir)) {
    return "~${File.separator}${userHomeDir.relativize(file)}"
  }
  return file.toString()
}
