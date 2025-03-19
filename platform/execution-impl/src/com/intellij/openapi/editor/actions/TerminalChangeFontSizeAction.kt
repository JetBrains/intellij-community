// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.application.options.EditorFontsConstants
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.terminal.JBTerminalWidget
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class TerminalChangeFontSizeAction(private val myStep: Float) : DumbAwareAction(), LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    val terminalWidget = getTerminalWidget(e)
    if (terminalWidget != null) {
      val newFontSize = terminalWidget.fontSize2D + myStep
      if (newFontSize >= EditorFontsConstants.getMinEditorFontSize() && newFontSize <= EditorFontsConstants.getMaxEditorFontSize()) {
        terminalWidget.setFontSize(newFontSize)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getTerminalWidget(e) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  class IncreaseEditorFontSize : TerminalChangeFontSizeAction(1f)

  class DecreaseEditorFontSize : TerminalChangeFontSizeAction(-1f)

  companion object {
    fun getTerminalWidget(e: AnActionEvent): JBTerminalWidget? {
      return e.dataContext.getData(JBTerminalWidget.TERMINAL_DATA_KEY)
    }
  }
}