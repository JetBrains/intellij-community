// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.experimental.ExperimentalUiCollector
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.ExperimentalUI

class EnableNewUiAction : AnAction(), DumbAware {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = !ExperimentalUI.isNewUI()
    e.presentation.selectedIcon = AllIcons.Actions.EnableNewUiSelected
  }

  override fun actionPerformed(e: AnActionEvent) {
    ExperimentalUiCollector.logSwitchUi(ExperimentalUiCollector.SwitchSource.ENABLE_NEW_UI_ACTION, true)
    ExperimentalUI.setNewUI(true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
