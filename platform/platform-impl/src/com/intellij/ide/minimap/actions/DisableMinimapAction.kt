// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.actions

import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.ide.minimap.utils.MiniMessagesBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class DisableMinimapAction : AnAction(MiniMessagesBundle.message("action.disable")) {
  override fun isDumbAware() = true
  override fun actionPerformed(e: AnActionEvent) {
    val settings = MinimapSettings.getInstance()
    settings.state.enabled = false
    settings.settingsChangeCallback.notify(MinimapSettings.SettingsChangeType.WithUiRebuild)
  }
}