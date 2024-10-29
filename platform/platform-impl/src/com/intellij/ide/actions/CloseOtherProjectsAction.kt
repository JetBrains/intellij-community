// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.IdeUICustomization
import org.jetbrains.annotations.ApiStatus

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
class CloseOtherProjectsAction : CloseProjectsActionBase() {
  init {
    val uiCustomization = IdeUICustomization.getInstance()
    templatePresentation.setText(uiCustomization.projectMessagePointer("action.close.other.projects.text"))
    templatePresentation.setDescription(uiCustomization.projectMessagePointer("action.close.other.projects.description"))
  }

  override fun canClose(project: Project, currentProject: Project): Boolean = project !== currentProject

  override fun shouldShow(e: AnActionEvent): Boolean = ProjectManager.getInstance().openProjects.size > 1

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}