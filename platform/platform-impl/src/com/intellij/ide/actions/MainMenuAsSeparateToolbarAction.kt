// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.ExperimentalUI

class MainMenuAsSeparateToolbarAction : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  override fun isSelected(e: AnActionEvent): Boolean {
    return UISettings.getInstance().separateMainMenu
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    var restartNeeded = false

    if (SystemInfoRt.isUnix && !SystemInfoRt.isMac) {
      val result = Messages.showYesNoCancelDialog(
        IdeBundle.message("dialog.message.restarted.to.apply.changes",
                          ApplicationNamesInfo.getInstance().fullProductName),
        ActionsBundle.message("action.MainMenuAsSeparateToolbarAction.text"),
        IdeBundle.message("ide.restart.action"),
        IdeBundle.message("ide.notnow.action"),
        CommonBundle.getCancelButtonText(),
        Messages.getQuestionIcon())

      when (result) {
        Messages.YES -> {
          restartNeeded = true
        }
        Messages.NO -> {}
        else -> return
      }
    }

    val uiSettings = UISettings.getInstance()
    uiSettings.separateMainMenu = state

    if (restartNeeded) {
      ApplicationManagerEx.getApplicationEx().restart(true)
    }
    else {
      uiSettings.fireUISettingsChanged()
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = ExperimentalUI.isNewUI() && !SystemInfo.isMac
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
