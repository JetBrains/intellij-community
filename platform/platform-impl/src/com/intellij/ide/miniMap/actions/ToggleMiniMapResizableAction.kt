// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.miniMap.actions

import com.intellij.ide.miniMap.settings.MiniMapSettings
import com.intellij.ide.miniMap.utils.MiniMessagesBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction

class ToggleMiniMapResizableAction : CheckboxAction(MiniMessagesBundle.message("action.resizable")) {
  override fun isDumbAware() = true
  override fun isSelected(e: AnActionEvent): Boolean = MiniMapSettings.getInstance().state.resizable
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    MiniMapSettings.getInstance().state.resizable = state
  }
}