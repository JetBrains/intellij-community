// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UIDensity
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.ui.ExperimentalUI

/**
 * @author Konstantin Bulenkov
 */
class ToggleCompactModeAction: DumbAwareToggleAction() {

  override fun isSelected(e: AnActionEvent) = UISettings.getInstance().uiDensity == UIDensity.COMPACT

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val value = UISettings.getInstance().uiDensity
    val newValue = if (state) UIDensity.COMPACT else UIDensity.DEFAULT
    if (newValue != value) {
      UISettings.getInstance().uiDensity = newValue
      LafManager.getInstance().applyDensity()
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = ExperimentalUI.isNewUI()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
