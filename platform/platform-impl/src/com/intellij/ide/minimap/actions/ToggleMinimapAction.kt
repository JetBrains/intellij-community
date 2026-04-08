// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.actions

import com.intellij.ide.minimap.MinimapRegistry
import com.intellij.ide.minimap.MinimapUsageCollector
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareToggleAction

class ToggleMinimapAction : DumbAwareToggleAction(), RightAlignedToolbarAction, ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = MinimapRegistry.isEnabled()
  }

  override fun isSelected(e: AnActionEvent): Boolean = MinimapSettings.getInstance().state.enabled
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val settings = MinimapSettings.getInstance()
    val currentState = settings.state
    if (currentState.enabled == state) return
    currentState.enabled = state
    MinimapUsageCollector.logToggled(
      enabled = state,
      source = MinimapUsageCollector.ToggleSource.ACTION_TOGGLE,
      scaleMode = currentState.scaleMode,
      rightAligned = currentState.rightAligned,
    )
    settings.settingsChangeCallback.notify(MinimapSettings.SettingsChangeType.WithUiRebuild)
  }
}
