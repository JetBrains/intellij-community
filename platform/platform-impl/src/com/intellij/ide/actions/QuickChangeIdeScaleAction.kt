// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.ui.percentStringValue
import com.intellij.ide.ui.percentValue
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project

class QuickChangeIdeScaleAction : QuickSwitchSchemeAction() {
  override fun fillActions(project: Project?, group: DefaultActionGroup, dataContext: DataContext) {
    val settingsUtils = UISettingsUtils.instance

    IdeScaleTransformer.Settings.currentScaleOptions.forEach { scale ->
      val title = scale.percentStringValue
      group.add(object : DumbAwareToggleAction(title) {
        override fun isSelected(e: AnActionEvent): Boolean = settingsUtils.currentIdeScale.percentValue == scale.percentValue

        override fun setSelected(e: AnActionEvent, state: Boolean) {
          if (state && settingsUtils.currentIdeScale.percentValue != scale.percentValue) {
            settingsUtils.setCurrentIdeScale(scale)
            UISettings.getInstance().fireUISettingsChanged()
          }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
      })
    }
  }

  override fun isEnabled(): Boolean = IdeScaleTransformer.Settings.currentScaleOptions.isNotEmpty()
}
