// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt

internal class RecentProjectsGroup : ActionGroup(), DumbAware, ActionRemoteBehaviorSpecification.BackendOnly {
  init {
    val presentation = getTemplatePresentation()
    presentation.setText(ActionsBundle.messagePointer(if (SystemInfoRt.isMac) "group.reopen.mac.text" else "group.reopen.win.text"))
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return removeCurrentProject(e?.project)
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    presentation.setEnabled(!RecentProjectListActionProvider.getInstance().getActionsWithoutGroups(addClearListItem = true).isEmpty())
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun removeCurrentProject(project: Project?): Array<AnAction> {
  return RecentProjectListActionProvider.getInstance()
    .getActionsWithoutGroups(addClearListItem = true, withoutProject = project)
    .toTypedArray()
}

