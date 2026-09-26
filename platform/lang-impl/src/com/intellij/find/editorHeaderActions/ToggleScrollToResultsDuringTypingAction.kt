// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions

import com.intellij.find.FindSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

internal class ToggleScrollToResultsDuringTypingAction : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean {
    return FindSettings.getInstance().isScrollToResultsDuringTyping()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    FindSettings.getInstance().setScrollToResultsDuringTyping(state)
  }
}