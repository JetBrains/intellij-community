// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions

import com.intellij.ide.actions.CopyElementAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroupUtil
import com.intellij.openapi.actionSystem.ActionGroupWrapper
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.annotations.Unmodifiable

private fun isRefactoringMenuInCodeMenu(): Boolean = RegistryManager.getInstance().`is`("ide.menu.merge.refactor.to.code")

/**
 * The Refactor menu.
 * When the `ide.menu.merge.refactor.to.code` registry key is enabled, the group hides itself in the main menu.
 * [CodeMenuRefactoringGroup] then shows it in the Code menu.
 */
internal class RefactoringMenuGroup : DefaultActionGroup(), DumbAware {

  override fun postProcessVisibleChildren(e: AnActionEvent, visibleChildren: List<AnAction>): @Unmodifiable List<AnAction> {
    if (e.place == ActionPlaces.PROJECT_VIEW_POPUP || e.place == ActionPlaces.EDITOR_POPUP) {
      // Rename Element is added to the top level of the project view context menu (IJPL-163103)
      if (isOnlyFileActions(visibleChildren)) {
        // filter out separators contributed by RefactoringMenu group
        return visibleChildren.filter { it !is RenameElementAction && it !is Separator }
      }
      return visibleChildren.filter { it !is RenameElementAction }
    }
    return super.postProcessVisibleChildren(e, visibleChildren)
  }


  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = e.place != ActionPlaces.MAIN_MENU || !isRefactoringMenuInCodeMenu()
    val actions = ActionGroupUtil.getActiveActions(this, e)
    e.presentation.isPopupGroup = e.place != ActionPlaces.PROJECT_VIEW_POPUP || !isOnlyFileActions(actions)
  }

  private fun isOnlyFileActions(actions: Iterable<AnAction>): Boolean =
    actions.all { it is MoveAction || it is CopyElementAction || it is RenameElementAction || it is RefactoringQuickListPopupAction || it is Separator }
}

/**
 * Shows the Refactor menu in the Code menu when the `ide.menu.merge.refactor.to.code` registry key is enabled.
 * The update session keeps one presentation for each action instance.
 * Thus, the group shows a wrapper of [RefactoringMenuGroup], which the main menu hides.
 */
internal class CodeMenuRefactoringGroup : ActionGroup(), DumbAware {
  private val refactoringMenu: AnAction by lazy {
    object : ActionGroupWrapper(ActionManager.getInstance().getAction(IdeActions.GROUP_REFACTOR) as ActionGroup) {
      override fun update(e: AnActionEvent) {
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return if (isRefactoringMenuInCodeMenu()) arrayOf(refactoringMenu) else EMPTY_ARRAY
  }
}
