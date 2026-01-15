// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.impl.welcomeScreen.projectActions.repaintFrame

internal class UseProjectColorsCheckboxAction : CheckboxAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.place == ActionPlaces.getPopupPlace(ActionPlaces.MAIN_TOOLBAR) &&
                                         ProjectWindowCustomizerService.getInstance().isAvailable()
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return UISettings.getInstance().differentiateProjects
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val uiSettings = UISettings.getInstance()
    uiSettings.differentiateProjects = state
    uiSettings.fireUISettingsChanged()
    repaintFrame(e.project)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}