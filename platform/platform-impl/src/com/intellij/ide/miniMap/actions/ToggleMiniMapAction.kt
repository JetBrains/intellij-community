// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.miniMap.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.miniMap.settings.MiniMapSettings
import com.intellij.ide.miniMap.utils.MiniMessagesBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.project.DumbAwareToggleAction

class ToggleMiniMapAction : DumbAwareToggleAction(MiniMessagesBundle.message("action.toggle"), null,
                                                  AllIcons.Actions.Minimap), RightAlignedToolbarAction {
  override fun isSelected(e: AnActionEvent) = MiniMapSettings.getInstance().state.enabled
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val settings = MiniMapSettings.getInstance()
    settings.state.enabled = state
    settings.settingsChangeCallback.notify(MiniMapSettings.SettingsChangeType.WithUiRebuild)
  }
}