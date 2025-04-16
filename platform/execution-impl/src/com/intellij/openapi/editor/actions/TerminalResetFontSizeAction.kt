// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions

import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.actions.TerminalChangeFontSizeAction.Companion.getHandler
import com.intellij.openapi.project.DumbAwareAction

private class TerminalResetFontSizeAction : DumbAwareAction(), LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    val handler = getHandler(e) ?: return
    handler.resetTerminalFontSize()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getHandler(e) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}