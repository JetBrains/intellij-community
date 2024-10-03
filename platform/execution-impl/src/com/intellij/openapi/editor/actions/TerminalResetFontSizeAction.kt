// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.actions.TerminalChangeFontSizeAction.Companion.getTerminalWidget
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TerminalResetFontSizeAction : DumbAwareAction(), LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    getTerminalWidget(e)?.resetFontSize()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getTerminalWidget(e) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}