// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.IdeUICustomization

/**
 * @author Konstantin Bulenkov
 */
class CloseAllProjectsAction : CloseProjectsActionBase() {
  init {
    val uiCustomization = IdeUICustomization.getInstance()
    templatePresentation.setText(uiCustomization.projectMessagePointer("action.close.all.projects.text"))
    templatePresentation.setDescription(uiCustomization.projectMessage("action.close.all.projects.description"))
  }

  override fun canClose(project: Project, currentProject: Project) = true

  override fun shouldShow(e: AnActionEvent) = ProjectManager.getInstance().openProjects.size > 1

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}