// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suppress

import com.intellij.inlinePrompt.isInlinePromptShown
import com.intellij.openapi.editor.Editor

internal class InlineCompletionSuppressStateByInlinePromptSupplier : InlineCompletionSuppressStateSupplier {
  override fun isSuppressed(editor: Editor): Boolean {
    return isInlinePromptShown(editor)
  }
}
