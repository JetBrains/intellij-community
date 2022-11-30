// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable

class ToggleInlayHintsGloballyAction : ToggleAction(CodeInsightBundle.message("inlay.hints.toggle.action")), Toggleable {
  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return InlayHintsSwitch.isEnabled(project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    InlayHintsSwitch.setEnabled(project, state)
  }
}