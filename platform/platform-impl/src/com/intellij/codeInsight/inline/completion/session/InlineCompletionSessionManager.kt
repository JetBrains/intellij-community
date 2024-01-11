// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.DefaultInlineCompletionOvertyper
import com.intellij.codeInsight.inline.completion.InlineCompletionOvertyper
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionEventBasedSuggestionUpdater
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt

internal abstract class InlineCompletionSessionManager {

  private var currentSession: InlineCompletionSession? = null

  /**
   * Updates [session] according to [result].
   *
   * @see invalidate
   * @see updateSession
   */
  @RequiresEdt
  protected abstract fun onUpdate(session: InlineCompletionSession, result: UpdateSessionResult)

  @RequiresEdt
  fun sessionCreated(newSession: InlineCompletionSession) {
    check(currentSession == null) { "Session already exists." }
    currentSession = newSession
  }

  @RequiresEdt
  fun sessionRemoved() {
    check(currentSession != null) { "Session does not exist." }
    currentSession = null
  }

  /**
   * Calls [onUpdate] with passed [UpdateSessionResult.Invalidated], if session exists.
   */
  @RequiresEdt
  fun invalidate() {
    currentSession?.let { session -> onUpdate(session, UpdateSessionResult.Invalidated) }
  }

  /**
   * If session does not exist at this moment, then it immediately returns `false`.
   *
   * Otherwise, it tries to update current session with [request].
   * After that, it results in one of [UpdateSessionResult], and calls [onUpdate] with this result.
   *
   * @return `false` if session does not exist.
   * Otherwise, whether [onUpdate] was called with either [UpdateSessionResult.Succeeded] or [UpdateSessionResult.Changed], meaning that
   * the update succeeded.
   *
   * @see InlineCompletionOvertyper
   */
  @RequiresEdt
  @RequiresBlockingContext
  fun updateSession(request: InlineCompletionRequest): Boolean {
    val session = currentSession
    if (session == null) {
      return false
    }
    if (session.provider.restartOn(request.event)) {
      invalidate(session)
      return false
    }

    val result = updateSession(session, request)
    onUpdate(session, result)
    return result != UpdateSessionResult.Invalidated
  }

  private fun invalidate(session: InlineCompletionSession) = onUpdate(session, UpdateSessionResult.Invalidated)

  private fun updateSession(session: InlineCompletionSession, request: InlineCompletionRequest): UpdateSessionResult {
    session.context.expectedStartOffset = request.endOffset

    val provider = session.provider
    val overtyper = provider.overtyper
    // Preserving back compatibility
    val updater = if (overtyper::class != DefaultInlineCompletionOvertyper::class) overtyper else provider.suggestionUpdater
    return updateSession(session, updater, request)
  }

  private fun updateSession(
    session: InlineCompletionSession,
    suggestionUpdater: InlineCompletionEventBasedSuggestionUpdater,
    request: InlineCompletionRequest
  ): UpdateSessionResult {
    val event = request.event

    if (session.getVariantsNumber() == null) { // variants are not provided yet
      return when (suggestionUpdater.updateWhenAwaitingVariants(event)) {
        true -> UpdateSessionResult.Succeeded
        false -> UpdateSessionResult.Invalidated
      }
    }

    val success = session.update { variant -> suggestionUpdater.update(event, variant) }
    return if (success) {
      check(!session.context.isDisposed)
      if (session.context.textToInsert().isEmpty()) UpdateSessionResult.Emptied else UpdateSessionResult.Succeeded
    }
    else {
      UpdateSessionResult.Invalidated
    }
  }

  // TODO update docs
  protected enum class UpdateSessionResult {
    Succeeded,
    Invalidated,
    Emptied
  }
}
