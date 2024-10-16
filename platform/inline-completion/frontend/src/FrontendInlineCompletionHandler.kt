// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.frontend

import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.CoroutineScope

class FrontendInlineCompletionHandler(
  scope: CoroutineScope,
  editor: Editor,
  parentDisposable: Disposable
) : InlineCompletionHandler(scope, editor, parentDisposable) {

  override fun startSessionOrNull(request: InlineCompletionRequest, provider: InlineCompletionProvider): InlineCompletionSession? {
    return initSession(request, provider)
  }
}
