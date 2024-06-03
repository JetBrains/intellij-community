// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace.configuration

import com.intellij.ide.workspace.getAllSubprojects
import com.intellij.ide.workspace.isWorkspace
import com.intellij.ide.workspace.isWorkspaceSupportEnabled
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal abstract class BaseWorkspaceAction(private val workspaceOnly: Boolean): DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = isWorkspaceSupportEnabled &&
                                         project != null &&
                                         (workspaceOnly && project.isWorkspace
                                         || !workspaceOnly && getAllSubprojects(project).isNotEmpty())
  }
}