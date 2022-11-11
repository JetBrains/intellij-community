// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.miniMap.actions

import com.intellij.ide.actions.SmartPopupActionGroup
import com.intellij.ide.miniMap.settings.FilterType
import com.intellij.ide.miniMap.settings.MiniMapSettings
import com.intellij.ide.miniMap.utils.MiniMessagesBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

class MiniMapFilterActionGroup : SmartPopupActionGroup() {
  init {
    templatePresentation.text = MiniMessagesBundle.message("action.filter")

    for (field in FilterType.values()) {
      add(object : ToggleAction(field.title, null, null) {
        override fun isSelected(e: AnActionEvent) = MiniMapSettings.getInstance().state.filterType == field
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
        override fun setSelected(e: AnActionEvent, state: Boolean) {
          val settings = MiniMapSettings.getInstance()
          settings.state.filterType = field
          settings.settingsChangeCallback.notify(MiniMapSettings.SettingsChangeType.WithUiRebuild)
        }
      })
    }
  }
}