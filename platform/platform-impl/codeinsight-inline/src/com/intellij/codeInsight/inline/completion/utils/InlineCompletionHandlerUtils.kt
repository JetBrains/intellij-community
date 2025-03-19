// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.utils

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.editor.Editor

internal object InlineCompletionHandlerUtils {
  fun hideInlineCompletion(editor: Editor, finishType: FinishType) {
    val context = InlineCompletionContext.getOrNull(editor) ?: return
    InlineCompletion.getHandlerOrNull(editor)?.hide(context, finishType)
  }
}
