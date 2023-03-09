// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project

class QuickChangeToolWindowLayout : QuickSwitchSchemeAction() {

  override fun fillActions(project: Project?, group: DefaultActionGroup, dataContext: DataContext) {
    getChildren().forEach { group.add(it) }
  }

  private fun getChildren(): Array<out AnAction> {
    val sourceGroup = ActionManager.getInstance().getAction(RestoreNamedLayoutActionGroup.ID) as ActionGroup
    return sourceGroup.getChildren(null)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = getChildren().size > 1
  }

}
