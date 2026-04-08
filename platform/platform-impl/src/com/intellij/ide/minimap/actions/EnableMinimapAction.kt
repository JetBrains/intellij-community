// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.actions

import com.intellij.ide.minimap.MinimapUsageCollector
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class EnableMinimapAction : AnAction() {
  override fun isDumbAware(): Boolean = true

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = !MinimapSettings.getInstance().state.enabled
  }

  override fun actionPerformed(e: AnActionEvent) {
    val settings = MinimapSettings.getInstance()
    val currentState = settings.state
    if (currentState.enabled) return
    currentState.enabled = true
    MinimapUsageCollector.logToggled(
      enabled = true,
      source = MinimapUsageCollector.ToggleSource.ACTION_ENABLE,
      scaleMode = currentState.scaleMode,
      rightAligned = currentState.rightAligned,
    )
    settings.settingsChangeCallback.notify(MinimapSettings.SettingsChangeType.WithUiRebuild)
  }
}
