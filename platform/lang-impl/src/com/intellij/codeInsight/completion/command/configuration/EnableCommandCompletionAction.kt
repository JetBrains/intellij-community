// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.configuration

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EnableCommandCompletionAction : ToggleAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean {
    val commandCompletionService = ApplicationCommandCompletionService.getInstance()
    return commandCompletionService.commandCompletionEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val commandCompletionService = ApplicationCommandCompletionService.getInstance()
    commandCompletionService.state.setEnabled(state)
  }
}
