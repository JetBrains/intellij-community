// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.terminal.JBTerminalPanel
import com.jediterm.terminal.ui.TerminalAction
import java.awt.event.KeyEvent

class TerminalActionWrapper(
  private val terminalAction: TerminalAction,
  terminalPanel: JBTerminalPanel,
) : AnAction(), DumbAware {

  init {
    val keyCode = terminalAction.keyCode
    if (keyCode != 0) {
      registerCustomShortcutSet(keyCode, terminalAction.modifiers, terminalPanel)
    }
    // The mnemonic is private, so we use this ugly hack to indirectly get to it:
    val menuItem = terminalAction.toMenuItem()
    templatePresentation.setTextWithMnemonic {
      val text = terminalAction.name
      val mnemonicChar = menuItem.mnemonic.toChar()
      val effectiveMnemonicChar = if (text.contains(mnemonicChar)) {
        mnemonicChar
      } else {
        0.toChar()
      }
      TextWithMnemonic.fromPlainText(text, effectiveMnemonicChar)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    terminalAction.actionPerformed(e.maybeKeyEvent)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.apply {
      isEnabled = terminalAction.isEnabled(e.maybeKeyEvent)
    }
  }

}

private val AnActionEvent.maybeKeyEvent: KeyEvent? get() = inputEvent as? KeyEvent?
