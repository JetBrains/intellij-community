// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionPrefixTruncator
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
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
   * Otherwise, it tries to update current session with [request] and its [provider].
   * After that, it results in one of [UpdateSessionResult], and calls [onUpdate] with this result.
   *
   * @return `false` if session does not exist.
   * Otherwise, whether [onUpdate] was called with either [UpdateSessionResult.Same] or [UpdateSessionResult.PrefixTruncated], meaning that
   * the update succeeded.
   *
   * @see InlineCompletionPrefixTruncator
   */
  @RequiresEdt
  @RequiresBlockingContext
  fun updateSession(request: InlineCompletionRequest, provider: InlineCompletionProvider?): Boolean {
    val session = currentSession
    if (session == null) {
      return false
    }
    if (session.provider.restartOn(request.event)) {
      invalidate(session)
      return false
    }
    if (provider == null && !session.context.isCurrentlyDisplaying()) {
      return true // Fast fall to not slow down editor
    }
    if (provider != null && session.provider != provider) {
      invalidate(session)
      return false
    }
    val result = updateContext(session.context, session.provider.prefixTruncator, request)
    onUpdate(session, result)
    return result !is UpdateSessionResult.Invalidated
  }

  private fun invalidate(session: InlineCompletionSession) = onUpdate(session, UpdateSessionResult.Invalidated)

  private fun updateContext(
    context: InlineCompletionContext,
    prefixTruncator: InlineCompletionPrefixTruncator,
    request: InlineCompletionRequest
  ): UpdateSessionResult {
    return when (request.event) {
      is InlineCompletionEvent.DocumentChange -> {
        val updatedResult = prefixTruncator.truncate(context, request.event.typing)?.let { truncated ->
          UpdateSessionResult.PrefixTruncated(truncated.elements, truncated.truncatedLength, request.endOffset)
        }
        updatedResult ?: UpdateSessionResult.Invalidated
      }
      is InlineCompletionEvent.InlineLookupEvent -> {
        if (context.isCurrentlyDisplaying()) UpdateSessionResult.Same else UpdateSessionResult.Invalidated
      }
      else -> UpdateSessionResult.Invalidated
    }
  }

  protected sealed interface UpdateSessionResult {
    class PrefixTruncated(
      val newElements: List<InlineCompletionElement>,
      val truncatedLength: Int,
      val newOffset: Int
    ) : UpdateSessionResult

    data object Same : UpdateSessionResult

    data object Invalidated : UpdateSessionResult
  }
}
