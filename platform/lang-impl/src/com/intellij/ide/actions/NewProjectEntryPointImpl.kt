// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.util.projectWizard.AbstractNewProjectDialog
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Resolves the New Project action in three steps.
 *
 * It takes the action [WELCOME_SCREEN_NEW_PROJECT_ACTION_ID], which IDEA registers.
 * It takes the leaf action of the group [NEW_PROJECT_OR_MODULE_GROUP_ID] with an id from [NEW_PROJECT_ACTION_IDS].
 * It shows the generator based dialog of the platform.
 */
@ApiStatus.Internal
class NewProjectEntryPointImpl : NewProjectEntryPoint {

  override fun findNewProjectAction(): AnAction? {
    val actionManager = ActionManager.getInstance()
    val welcomeScreenAction = actionManager.getAction(WELCOME_SCREEN_NEW_PROJECT_ACTION_ID)
    if (welcomeScreenAction != null) {
      return welcomeScreenAction
    }
    val group = actionManager.getAction(NEW_PROJECT_OR_MODULE_GROUP_ID) as? DefaultActionGroup ?: return null
    val ids = collectActionIds(group, actionManager, HashSet(), HashSet())
    val id = NEW_PROJECT_ACTION_IDS.firstOrNull { it in ids } ?: return null
    return actionManager.getAction(id)
  }

  override fun performNewProjectAction(dataContext: DataContext) {
    val action = findNewProjectAction()
    if (action == null) {
      showDefaultNewProjectDialog()
      return
    }
    val event = AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.WELCOME_SCREEN, ActionUiKind.NONE, null)
    ActionUtil.performAction(action, event)
  }

  private fun showDefaultNewProjectDialog() {
    object : AbstractNewProjectDialog() {
      override fun createNewProjectStep(): AbstractNewProjectStep<*> = DefaultNewProjectStep()
    }.show()
  }

  private fun collectActionIds(
    group: DefaultActionGroup,
    actionManager: ActionManager,
    visitedGroups: MutableSet<DefaultActionGroup>,
    ids: MutableSet<String>,
  ): Set<String> {
    if (!visitedGroups.add(group)) {
      return ids
    }
    for (child in group.getChildren(actionManager)) {
      actionManager.getId(child)?.let { ids.add(it) }
      if (child is DefaultActionGroup) {
        collectActionIds(child, actionManager, visitedGroups, ids)
      }
    }
    return ids
  }

  companion object {
    /** The action of IDEA. Android replaces it at run time, so the service reads it on every call. */
    const val WELCOME_SCREEN_NEW_PROJECT_ACTION_ID: String = "WelcomeScreen.CreateNewProject"

    /** The group that the platform declares empty and every product fills. */
    const val NEW_PROJECT_OR_MODULE_GROUP_ID: String = "NewProjectOrModuleGroup"

    /** The new project ids, in the order of preference. A product adds a module action and a clone action to the same group. */
    val NEW_PROJECT_ACTION_IDS: List<String> = listOf("NewProject", "NewDirectoryProject")
  }
}
