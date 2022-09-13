// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.actions

import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.ide.minimap.utils.MiniMessagesBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class MoveMinimapAction : AnAction(MiniMessagesBundle.message("action.moveLeft")) {
  override fun isDumbAware() = true
  override fun update(e: AnActionEvent) {
    e.presentation.text = if (MinimapSettings.getInstance().state.rightAligned) MiniMessagesBundle.message("action.moveLeft")
    else MiniMessagesBundle.message("action.moveRight")
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
  override fun actionPerformed(e: AnActionEvent) {
    val settings = MinimapSettings.getInstance()
    settings.state.rightAligned = !settings.state.rightAligned
    settings.settingsChangeCallback.notify(MinimapSettings.SettingsChangeType.WithUiRebuild)
  }
}