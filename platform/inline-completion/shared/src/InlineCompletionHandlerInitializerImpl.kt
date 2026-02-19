// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.CoroutineScope

internal class InlineCompletionHandlerInitializerImpl : InlineCompletionHandlerInitializer {
  override fun initialize(editor: Editor, scope: CoroutineScope, disposable: Disposable): InlineCompletionHandler {
    return InlineCompletionHandlerImpl(scope, editor, disposable)
  }
}
