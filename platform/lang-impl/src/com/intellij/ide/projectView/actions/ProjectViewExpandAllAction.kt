// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.TREE_EXPANDER
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental

@ApiStatus.Internal
@Experimental
class ProjectViewExpandAllAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {

  override fun actionPerformed(e: AnActionEvent) {
    val expander = getTreeExpander(e) ?: return
    if (expander.canExpand()) {
      expander.expandAll()
    }
  }

  override fun update(event: AnActionEvent) {
    val expander = getTreeExpander(event)
    event.presentation.isVisible = false
    event.presentation.isEnabled = expander != null && expander.canExpand() && !expander.isExpandAllEnabled
                                   Registry.`is`("ide.project.view.replace.expand.all.with.expand.recursively", true)
  }

  private fun getTreeExpander(e: AnActionEvent) = e.getData(TREE_EXPANDER)

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
