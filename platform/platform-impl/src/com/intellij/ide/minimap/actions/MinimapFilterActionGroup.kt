// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.actions

import com.intellij.ide.actions.SmartPopupActionGroup
import com.intellij.ide.minimap.settings.FilterType
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.ide.minimap.utils.MiniMessagesBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

class MinimapFilterActionGroup : SmartPopupActionGroup() {
  init {
    templatePresentation.text = MiniMessagesBundle.message("action.filter")

    for (field in FilterType.values()) {
      add(object : ToggleAction(field.title, null, null) {
        override fun isSelected(e: AnActionEvent) = MinimapSettings.getInstance().state.filterType == field
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
        override fun setSelected(e: AnActionEvent, state: Boolean) {
          val settings = MinimapSettings.getInstance()
          settings.state.filterType = field
          settings.settingsChangeCallback.notify(MinimapSettings.SettingsChangeType.WithUiRebuild)
        }
      })
    }
  }
}