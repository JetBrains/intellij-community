// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.text.TextWithMnemonic
import com.jediterm.terminal.ui.TerminalAction
import java.awt.event.KeyEvent

internal class TerminalActionWrapper(private val terminalAction: TerminalAction) : DumbAwareAction() {

  init {
    templatePresentation.setTextWithMnemonic {
      val text = terminalAction.name
      val mnemonicChar = terminalAction.mnemonicKeyCode?.let {
        if (text.contains(it.toChar())) it.toChar() else null
      } ?: 0.toChar()
      TextWithMnemonic.fromPlainText(text, mnemonicChar)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    terminalAction.actionPerformed(e.maybeKeyEvent)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = terminalAction.isEnabled(e.maybeKeyEvent)
  }
}

private val AnActionEvent.maybeKeyEvent: KeyEvent? get() = inputEvent as? KeyEvent
