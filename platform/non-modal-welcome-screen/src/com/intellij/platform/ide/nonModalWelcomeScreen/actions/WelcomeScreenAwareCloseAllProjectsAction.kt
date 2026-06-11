package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.actions.CloseAllProjectsAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.findProjectClosingTransitionHandler
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.nonModalWelcomeScreen.isWelcomeExperienceProjectSync
import kotlinx.coroutines.launch

internal class WelcomeScreenAwareCloseAllProjectsAction : CloseAllProjectsAction() {
  override fun shouldShow(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return !project.isWelcomeExperienceProjectSync() || ProjectManager.getInstance().openProjects.size > 1
  }

  override fun actionPerformed(e: AnActionEvent) {
    val currentProject = getProjectEvenIfNotInitialized(e) ?: return super.actionPerformed(e)
    val projectManager = ProjectManager.getInstance()
    val otherOpenProjects = projectManager.openProjects.filter { it != currentProject }

    val closeHandler = findProjectClosingTransitionHandler(currentProject) ?: return super.actionPerformed(e)

    otherOpenProjects.forEach {
      WindowManager.getInstance().updateDefaultFrameInfoOnProjectClose(it)
      WriteIntentReadAction.run {
        projectManager.closeAndDispose(it)
      }
      RecentProjectsManager.getInstance().updateLastProjectPath()
    }

    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      closeHandler()
    }
  }
}
