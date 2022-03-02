// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.application.options.EditorFontsConstants
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.terminal.JBTerminalWidget
import java.util.function.Supplier

sealed class TerminalChangeFontSizeAction(text: Supplier<String?>, private val myStep: Float) : DumbAwareAction(text), LightEditCompatible {
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

  class IncreaseEditorFontSize : TerminalChangeFontSizeAction(EditorBundle.messagePointer("increase.editor.font"), 1f)

  class DecreaseEditorFontSize : TerminalChangeFontSizeAction(EditorBundle.messagePointer("decrease.editor.font"), -1f)

  companion object {
    fun getTerminalWidget(e: AnActionEvent): JBTerminalWidget? {
      return e.dataContext.getData(JBTerminalWidget.TERMINAL_DATA_KEY)
    }
  }
}