// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.ExperimentalUI

class MainMenuAsSeparateToolbarAction : ToggleAction(), DumbAware {

  override fun isSelected(e: AnActionEvent): Boolean {
    return UISettings.getInstance().separateMainMenu
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val uiSettings = UISettings.getInstance()
    uiSettings.separateMainMenu = state
    uiSettings.fireUISettingsChanged()
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = ExperimentalUI.isNewUI() && (SystemInfo.isWindows || SystemInfo.isXWindow)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
