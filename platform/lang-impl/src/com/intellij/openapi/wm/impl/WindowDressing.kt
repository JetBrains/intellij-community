// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.ActiveWindowsWatcher
import com.intellij.ide.IdeDependentAction
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.lightEdit.LightEditServiceListener
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.mac.MacWinTabsHandler
import javax.swing.JFrame

private fun getWindowActionGroup(): ProjectWindowActionGroup {
  return ActionManager.getInstance().getAction("OpenProjectWindows") as ProjectWindowActionGroup
}

private suspend fun getWindowActionGroupAsync(): ProjectWindowActionGroup {
  return serviceAsync<ActionManager>().getAction("OpenProjectWindows") as ProjectWindowActionGroup
}

class WindowDressing : ProjectCloseListener, LightEditServiceListener {
  override fun projectClosed(project: Project) {
    getWindowActionGroup().removeProject(project)
  }

  override fun lightEditWindowOpened(project: Project) {
    getWindowActionGroup().addProject(project)
  }

  override fun lightEditWindowClosed(project: Project) {
    getWindowActionGroup().removeProject(project)
  }
}

internal class WindowDressingStartupActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    getWindowActionGroupAsync().addProject(project)
  }
}

internal class PreviousProjectWindow : IdeDependentAction(), DumbAware, LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    getWindowActionGroup().activatePreviousWindow(e)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.setEnabled(getWindowActionGroup().canActivatePrevious(e.getData(CommonDataKeys.PROJECT)))
    super.update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class NextProjectWindow : IdeDependentAction(), DumbAware, LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    getWindowActionGroup().activateNextWindow(e)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.setEnabled(getWindowActionGroup().canActivateNext(e.getData(CommonDataKeys.PROJECT)))
    super.update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class PreviousWindow : AbstractTraverseWindowAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    doPerform { ActiveWindowsWatcher.nextWindowBefore(it) }
  }

  override fun switchFullScreenFrame(frame: JFrame) {
    MacWinTabsHandler.switchFrameIfPossible(/* frame = */ frame, /* next = */ false)
  }
}

internal class NextWindow : AbstractTraverseWindowAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    doPerform { ActiveWindowsWatcher.nextWindowAfter(it) }
  }

  override fun switchFullScreenFrame(frame: JFrame) {
    MacWinTabsHandler.switchFrameIfPossible(/* frame = */ frame, /* next = */ true)
  }
}
