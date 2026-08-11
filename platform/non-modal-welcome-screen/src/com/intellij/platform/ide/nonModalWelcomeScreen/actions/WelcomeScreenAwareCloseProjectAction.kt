package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.ide.actions.CloseProjectAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ex.findProjectClosingTransitionHandler
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.nonModalWelcomeScreen.isWelcomeExperienceProjectSync
import kotlinx.coroutines.launch

internal class WelcomeScreenAwareCloseProjectAction : CloseProjectAction() {
  override fun shouldShow(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return !project.isWelcomeExperienceProjectSync() || ProjectManager.getInstance().openProjects.size > 1
  }

  override fun actionPerformed(e: AnActionEvent) {
    val currentProject = getProjectEvenIfNotInitialized(e)
    if (currentProject == null || currentProject.isWelcomeExperienceProjectSync() || ProjectManager.getInstance().openProjects.size > 1) {
      return super.actionPerformed(e)
    }

    val transitionHandler = findProjectClosingTransitionHandler(currentProject)
    if (transitionHandler == null) {
      return super.actionPerformed(e)
    }

    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      transitionHandler()
    }
  }
}
