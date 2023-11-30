// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.experimental.ExperimentalUiCollector
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.ExperimentalUI

private class DisableNewUiAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ExperimentalUI.isNewUI()
  }

  override fun actionPerformed(e: AnActionEvent) {
    ExperimentalUiCollector.logSwitchUi(ExperimentalUiCollector.SwitchSource.DISABLE_NEW_UI_ACTION, false)
    ExperimentalUI.setNewUI(value = false)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
