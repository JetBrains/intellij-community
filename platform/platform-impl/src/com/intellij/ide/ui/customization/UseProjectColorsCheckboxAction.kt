// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.ide.IdeBundle
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction

class UseProjectColorsCheckboxAction : CheckboxAction(IdeBundle.message("checkbox.use.solution.colours.in.toolbar")) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = ProjectWindowCustomizerService.getInstance().isAvailable()
                                         && e.place == ActionPlaces.MAIN_TOOLBAR
                                         && !UISettings.getInstance().separateMainMenu
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return UISettings.getInstance().differentiateProjects
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    UISettings.getInstance().differentiateProjects = state
    UISettings.getInstance().fireUISettingsChanged()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
};