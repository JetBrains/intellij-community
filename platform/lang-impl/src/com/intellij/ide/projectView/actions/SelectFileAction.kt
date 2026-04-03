// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

internal class SelectFileAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null || project.isDisposed) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    SelectFileActionService.getInstance(project).update(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null || project.isDisposed) {
      return
    }
    SelectFileActionService.getInstance(project).actionPerformed(e)
  }
}

@ApiStatus.Internal
interface SelectFileActionService {
  companion object {
    fun getInstance(project: Project): SelectFileActionService = project.service()
  }
  fun update(event: AnActionEvent)
  fun actionPerformed(event: AnActionEvent)
}
