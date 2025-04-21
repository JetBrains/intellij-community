// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.MainMenuDisplayMode
import com.intellij.ide.ui.UISettings
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.ExperimentalUI

internal class ChangeMainMenuModeActionGroup : DefaultActionGroup(), DumbAware {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return MainMenuDisplayMode.entries.map { ChangeMainMenuModeAction(it) }.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ExperimentalUI.isNewUI() && !SystemInfo.isMac
  }
}

private class ChangeMainMenuModeAction(private val mode: MainMenuDisplayMode) : DumbAware, ToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  init {
    templatePresentation.text = mode.description
    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.IfRequested
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean {
    return UISettings.getInstance().mainMenuDisplayMode == mode
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (!state) return
    var restartNeeded = false

    val uiSettings = UISettings.getInstance()
    if (SystemInfoRt.isUnix && !SystemInfoRt.isMac) {
      val currentDisplayMode = uiSettings.mainMenuDisplayMode
      if (currentDisplayMode == MainMenuDisplayMode.SEPARATE_TOOLBAR || mode == MainMenuDisplayMode.SEPARATE_TOOLBAR) {
        val result = Messages.showYesNoCancelDialog(
          IdeBundle.message("dialog.message.restarted.to.apply.changes",
                            ApplicationNamesInfo.getInstance().fullProductName),
          ActionsBundle.message("action.ChangeMainMenu.restart.dialog.title"),
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
    }

    uiSettings.mainMenuDisplayMode = mode

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
}
