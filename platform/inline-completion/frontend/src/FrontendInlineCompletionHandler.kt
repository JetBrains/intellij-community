// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.frontend

import com.intellij.codeInsight.inline.completion.*
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSessionManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.CoroutineScope

internal class FrontendInlineCompletionHandler(
  scope: CoroutineScope,
  editor: Editor,
  parentDisposable: Disposable
) : InlineCompletionHandler(scope, editor, parentDisposable) {

  override fun startSessionOrNull(request: InlineCompletionRequest, provider: InlineCompletionProvider): InlineCompletionSession? {
    return sessionManager.createSession(provider, request, parentDisposable, specificId = null)
  }

  override fun doHide(context: InlineCompletionContext, finishType: FinishType) {
    performHardHide(context, finishType)
  }

  override fun createSessionManager(): InlineCompletionSessionManager {
    return object : InlineCompletionSessionManagerBase(editor) {
      override fun executeHide(
        context: InlineCompletionContext,
        finishType: FinishType,
        invalidatedResult: UpdateSessionResult.Invalidated?
      ) {
        // TODO share with backend
        when (val reason = invalidatedResult?.reason) {
          is UpdateSessionResult.Invalidated.Reason.Event -> {
            invalidationListeners.multicaster.onInvalidatedByEvent(reason.event)
          }
          UpdateSessionResult.Invalidated.Reason.UnclassifiedDocumentChange -> {
            invalidationListeners.multicaster.onInvalidatedByUnclassifiedDocumentChange()
          }
          null -> Unit
        }
        hide(context, finishType)
      }

      override fun getSuggestionUpdater(provider: InlineCompletionProvider): InlineCompletionSuggestionUpdateManager {
        return provider.suggestionUpdateManager
      }
    }
  }

  override fun afterInsert(providerId: InlineCompletionProviderID) {
    // The session is completely destroyed at this moment, so it's safe to send a new event
    invokeEvent(InlineCompletionEvent.SuggestionInserted(editor, providerId))
  }
}
