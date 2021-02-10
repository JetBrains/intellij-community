// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.IdeUICustomization

/**
 * @author Konstantin Bulenkov
 */
class CloseOtherProjectsAction : CloseProjectsActionBase() {
  override fun canClose(project: Project, currentProject: Project) = project !== currentProject

  override fun shouldShow(e: AnActionEvent) = ProjectManager.getInstance().openProjects.size > 1

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.text = IdeUICustomization.getInstance().projectMessage("action.close.other.projects.text")
    e.presentation.description = IdeUICustomization.getInstance().projectMessage("action.close.other.projects.description")
  }
}