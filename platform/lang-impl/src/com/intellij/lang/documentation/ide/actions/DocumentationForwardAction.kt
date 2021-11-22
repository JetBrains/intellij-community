// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.util.ui.accessibility.ScreenReader

class DocumentationForwardAction : AnAction(
  CodeInsightBundle.messagePointer("javadoc.action.forward"),
  AllIcons.Actions.Forward
), ActionToIgnore {

  init {
    shortcutSet = CustomShortcutSet(
      ActionManager.getInstance().getKeyboardShortcut(IdeActions.ACTION_GOTO_FORWARD),
      KeyboardShortcut.fromString(if (ScreenReader.isActive()) "alt RIGHT" else "RIGHT"),
      KeymapUtil.parseMouseShortcut("button5")
    )
  }

  private fun history(e: AnActionEvent) = e.dataContext.getData(DOCUMENTATION_HISTORY_DATA_KEY)

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = history(e)?.canForward() == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    history(e)?.forward()
  }
}
