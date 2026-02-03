// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.shortcut

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class InlineCompletionShortcutHintState {
  SHOW_HINT,
  SHOW_CHOICE,
  SHOW_NOTHING;

  companion object {

    fun getState(): InlineCompletionShortcutHintState {
      val primaryShortcut = KeymapUtil.getPrimaryShortcut(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
      if (primaryShortcut?.isTab() == false) {
        InlineCompletionShortcutHintListener.choiceMade()
      }
      if (primaryShortcut == null || InlineCompletionShortcutHintListener.isHintDisabled()) {
        return SHOW_NOTHING
      }
      return if (InlineCompletionShortcutHintListener.shouldShowChoice() && !application.isUnitTestMode) SHOW_CHOICE else SHOW_HINT
    }

    private fun Shortcut.isTab(): Boolean {
      return toString().lowercase() == "[pressed tab]"
    }
  }
}
