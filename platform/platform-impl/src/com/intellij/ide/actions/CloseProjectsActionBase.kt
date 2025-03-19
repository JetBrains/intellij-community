// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ExitStarter
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.ui.ComponentUtil
import com.intellij.util.PlatformUtils

/**
 * @author Konstantin Bulenkov
 */
abstract class CloseProjectsActionBase : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  init {
    templatePresentation.isApplicationScope = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val currentProject = getProjectEvenIfNotInitialized(e) ?: return
    ProjectManager.getInstance().openProjects
      .filter { canClose(it, currentProject) }
      .forEach {
        // ensure that last closed project frame bounds will be used as newly created project frame bounds
        // (if will be no another focused opened project)
        WindowManager.getInstance().updateDefaultFrameInfoOnProjectClose(it)
        WriteIntentReadAction.run {
          ProjectManager.getInstance().closeAndDispose(it)
        }

        // RecentProjectsManager cannot distinguish close as part of exit (no need to remove project),
        // and close as explicit user initiated action (need to remove project), because reason is not provided to `projectClosed` event.
        RecentProjectsManager.getInstance().updateLastProjectPath()
      }

    showWelcomeFrameIfNeeded()
  }

  protected open fun showWelcomeFrameIfNeeded() {
    WelcomeFrame.showIfNoProjectOpened()
  }

  override fun update(e: AnActionEvent) {
    val project = getProjectEvenIfNotInitialized(e)
    e.presentation.isEnabledAndVisible = project != null && !project.isDefault && shouldShow(e)
  }

  protected fun getProjectEvenIfNotInitialized(e: AnActionEvent): Project? {
    return e.project ?: ProjectFrameHelper.getFrameHelper(
      ComponentUtil.getWindow(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)))?.project
  }

  protected abstract fun shouldShow(e: AnActionEvent): Boolean

  protected abstract fun canClose(project: Project, currentProject: Project): Boolean
}