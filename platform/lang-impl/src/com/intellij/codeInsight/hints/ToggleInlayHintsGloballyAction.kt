// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable

class ToggleInlayHintsGloballyAction : ToggleAction(CodeInsightBundle.message("inlay.hints.toggle.action")), Toggleable {
  override fun isSelected(e: AnActionEvent): Boolean {
    return InlayHintsSettings.instance().hintsEnabledGlobally()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    InlayHintsSettings.instance().setEnabledGlobally(state)
    CodeVisionSettings.instance().codeVisionEnabled = state
    InlayHintsPassFactory.forceHintsUpdateOnNextPass()
  }
}