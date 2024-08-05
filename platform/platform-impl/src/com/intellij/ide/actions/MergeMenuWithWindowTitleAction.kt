// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.MessageConstants
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.util.ui.RestartDialogImpl

class MergeMenuWithWindowTitleAction : ToggleAction(), DumbAware {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = CustomWindowHeaderUtil.hideNativeLinuxTitleAvailable && CustomWindowHeaderUtil.hideNativeLinuxTitleSupported
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return UISettings.getInstance().mergeMainMenuWithWindowTitle
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
  }

  override fun actionPerformed(e: AnActionEvent) {
    val result = RestartDialogImpl.showRestartRequired(showCancelButton = true, launchRestart = false)
    if (result == MessageConstants.CANCEL) {
      return
    }

    val settings = UISettings.getInstance()
    settings.mergeMainMenuWithWindowTitle = !settings.mergeMainMenuWithWindowTitle
    settings.fireUISettingsChanged()

    if (result == MessageConstants.YES) {
      ApplicationManagerEx.getApplicationEx().restart(true)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
