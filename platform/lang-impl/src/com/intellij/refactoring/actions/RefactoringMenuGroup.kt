// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroupWrapper
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.RegistryManager

private fun isRefactoringMenuInCodeMenu(): Boolean = RegistryManager.getInstance().`is`("ide.menu.merge.refactor.to.code")

/**
 * The Refactor menu.
 * When the `ide.menu.merge.refactor.to.code` registry key is enabled, the group hides itself in the main menu.
 * [CodeMenuRefactoringGroup] then shows it in the Code menu.
 */
internal class RefactoringMenuGroup : DefaultActionGroup() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = e.place != ActionPlaces.MAIN_MENU || !isRefactoringMenuInCodeMenu()
  }
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
