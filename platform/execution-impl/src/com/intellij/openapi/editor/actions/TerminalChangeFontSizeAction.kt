// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.application.options.EditorFontsConstants
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.TerminalFontSizeProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class TerminalChangeFontSizeAction(private val myStep: Float) : DumbAwareAction(), LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    getHandler(e)?.changeSize(myStep)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getHandler(e) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  class IncreaseEditorFontSize : TerminalChangeFontSizeAction(1f)

  class DecreaseEditorFontSize : TerminalChangeFontSizeAction(-1f)

  companion object {
    internal fun getHandler(e: AnActionEvent): TerminalChangeFontHandler? {
      val terminalWidget = getTerminalWidget(e)
      if (terminalWidget != null) {
        return ClassicTerminalChangeFontHandler(terminalWidget)
      }
      val editor = e.getData(CommonDataKeys.EDITOR)
      if (editor != null) {
        val strategy = editor.getUserData(TerminalFontSizeProvider.KEY)
        if (strategy != null) {
          return ReworkedTerminalChangeFontHandler(strategy)
        }
      }
      return null
    }

    private fun getTerminalWidget(e: AnActionEvent): JBTerminalWidget? {
      return e.dataContext.getData(JBTerminalWidget.TERMINAL_DATA_KEY)
    }
  }
}

internal sealed interface TerminalChangeFontHandler {
  fun changeSize(step: Float)
  fun resetTerminalFontSize()
}

private class ClassicTerminalChangeFontHandler(private val widget: JBTerminalWidget) : TerminalChangeFontHandler {
  override fun changeSize(step: Float) {
    val settingsProvider = widget.settingsProvider
    val newFontSize = settingsProvider.terminalFontSize + step
    if (newFontSize >= EditorFontsConstants.getMinEditorFontSize() && newFontSize <= EditorFontsConstants.getMaxEditorFontSize()) {
      settingsProvider.terminalFontSize = newFontSize
    }
  }

  override fun resetTerminalFontSize() {
    widget.settingsProvider.resetTerminalFontSize()
  }
}

private class ReworkedTerminalChangeFontHandler(private val provider: TerminalFontSizeProvider) : TerminalChangeFontHandler {
  override fun changeSize(step: Float) {
    // no range check here because the provider implementation takes care of it
    provider.setFontSize(provider.getFontSize() + step)
  }

  override fun resetTerminalFontSize() {
    provider.resetFontSize()
  }
}
