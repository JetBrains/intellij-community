package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.ide.actions.CloseAllProjectsAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.ide.nonModalWelcomeScreen.isWelcomeExperienceProjectSync

internal class WelcomeScreenAwareCloseAllProjectsAction : CloseAllProjectsAction() {
  override fun shouldShow(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return !project.isWelcomeExperienceProjectSync() || ProjectManager.getInstance().openProjects.size > 1
  }

  override fun canClose(project: Project, currentProject: Project): Boolean {
    return !project.isWelcomeExperienceProjectSync()
  }
}
