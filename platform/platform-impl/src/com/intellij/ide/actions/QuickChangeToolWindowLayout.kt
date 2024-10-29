// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class QuickChangeToolWindowLayout : QuickSwitchSchemeAction(), ActionRemoteBehaviorSpecification.Frontend {

  private val restoreLayoutsGroup by lazy { RestoreNamedLayoutActionGroup() }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun fillActions(project: Project?, group: DefaultActionGroup, dataContext: DataContext) {
    getChildren().forEach { group.add(it) }
  }

  private fun getChildren(): Array<out AnAction> {
    return restoreLayoutsGroup.getChildren(null)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = getChildren().size > 1
  }

}
