package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.ide.actions.CloseProjectAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider.Companion.isWelcomeScreenProject

internal class WelcomeScreenAwareCloseProjectAction : CloseProjectAction() {
  override fun shouldShow(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return !isWelcomeScreenProject(project) || ProjectManager.getInstance().openProjects.size > 1
  }
}