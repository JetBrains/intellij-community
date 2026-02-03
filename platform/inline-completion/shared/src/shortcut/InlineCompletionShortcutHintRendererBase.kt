// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.shortcut

import com.intellij.codeInsight.inline.hint.InlineShortcutHintRendererBase
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class InlineCompletionShortcutHintRendererBase(text: String?) : InlineShortcutHintRendererBase(text) {

  protected abstract fun isEnabledAdditional(editor: Editor): Boolean

  final override fun isEnabled(editor: Editor): Boolean {
    return isEnabledAdditional(editor) && !InlineCompletionShortcutHintListener.shouldHideHint(editor)
  }
}
