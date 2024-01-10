// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.DefaultInlineCompletionOvertyper
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionOvertyper
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
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
   * Otherwise, whether [onUpdate] was called with either [UpdateSessionResult.Same] or [UpdateSessionResult.Changed], meaning that
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
    return result !is UpdateSessionResult.Invalidated
  }

  private fun invalidate(session: InlineCompletionSession) = onUpdate(session, UpdateSessionResult.Invalidated)

  private fun updateSession(session: InlineCompletionSession, request: InlineCompletionRequest): UpdateSessionResult {
    session.context.expectedStartOffset = request.endOffset

    val provider = session.provider
    val overtyper = provider.overtyper
    return if (overtyper::class == DefaultInlineCompletionOvertyper::class) {
      // New behaviour
      updateSession(session, provider.suggestionUpdater, request)
    }
    else {
      // Preserving old behaviour
      updateSessionDeprecated(session.context, overtyper, request)
    }
  }

  private fun updateSession(
    session: InlineCompletionSession,
    suggestionUpdater: InlineCompletionEventBasedSuggestionUpdater,
    request: InlineCompletionRequest
  ): UpdateSessionResult {
    val event = request.event

    if (session.getVariantsNumber() == null) { // variants are not provided yet
      return when (suggestionUpdater.updateWhenAwaitingVariants(event)) {
        true -> UpdateSessionResult.Same
        false -> UpdateSessionResult.Invalidated
      }
    }

    val success = session.update { variant -> suggestionUpdater.update(event, variant) }
    return if (success) {
      check(!session.context.isDisposed)
      if (session.context.textToInsert().isEmpty()) UpdateSessionResult.Emptied else UpdateSessionResult.Same
    }
    else {
      UpdateSessionResult.Invalidated
    }
  }

  private fun updateSessionDeprecated(
    context: InlineCompletionContext,
    overtyper: InlineCompletionOvertyper,
    request: InlineCompletionRequest
  ): UpdateSessionResult {
    return when (request.event) {
      is InlineCompletionEvent.DocumentChange -> {
        val updatedResult = overtyper.overtype(context, request.event.typing)?.let { overtyped ->
          UpdateSessionResult.Changed(overtyped.elements, overtyped.overtypedLength, request.endOffset)
        }
        updatedResult ?: UpdateSessionResult.Invalidated
      }
      // If a provider didn't decide to restart on [request], then this event should not disturb the provider
      else -> UpdateSessionResult.Same
    }
  }

  protected sealed interface UpdateSessionResult {
    class Changed(
      val newElements: List<InlineCompletionElement>,
      val overtypedLength: Int,
      val newOffset: Int
    ) : UpdateSessionResult

    data object Same : UpdateSessionResult

    data object Invalidated : UpdateSessionResult

    data object Emptied : UpdateSessionResult
  }
}
