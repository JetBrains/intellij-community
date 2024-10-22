// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.frontend

import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionHandlerInitializer
import com.intellij.codeInsight.inline.completion.InlineCompletionRemDevUtils
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.CoroutineScope

internal class FrontendInlineCompletionHandlerInitializer : InlineCompletionHandlerInitializer {
  override fun initialize(editor: Editor, scope: CoroutineScope, disposable: Disposable): InlineCompletionHandler? {
    if (!ClientId.currentOrNull.isLocal && InlineCompletionRemDevUtils.isRhizomeUsed()) {
      return null
    }
    return FrontendInlineCompletionHandler(scope, editor, disposable)
  }
}
