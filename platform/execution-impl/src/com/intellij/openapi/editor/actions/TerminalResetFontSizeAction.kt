// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions

import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.actions.TerminalChangeFontSizeAction.Companion.getTerminalWidget
import com.intellij.openapi.project.DumbAwareAction

private class TerminalResetFontSizeAction : DumbAwareAction(), LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    val widget = getTerminalWidget(e) ?: return
    widget.settingsProvider.resetTerminalFontSize()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getTerminalWidget(e) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}