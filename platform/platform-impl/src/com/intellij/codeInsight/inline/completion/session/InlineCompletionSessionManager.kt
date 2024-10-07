// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.DefaultInlineCompletionOvertyper
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionOvertyper
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
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
  protected abstract fun onUpdate(session: InlineCompletionSession, event: InlineCompletionEvent?, result: UpdateSessionResult)

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
    currentSession?.let { session -> onUpdate(session, event = null, UpdateSessionResult.Invalidated) }
  }

  /**
   * If session does not exist at this moment, then it immediately returns `false`.
   *
   * Otherwise, it tries to update current session with [request].
   * After that, it results in one of [UpdateSessionResult], and calls [onUpdate] with this result.
   *
   * @return `false` if session does not exist.
   * Otherwise, whether [onUpdate] was called with either [UpdateSessionResult.Succeeded] or [UpdateSessionResult.Emptied], meaning that
   * the update succeeded.
   *
   * @see InlineCompletionOvertyper
   */
  @RequiresEdt
  fun updateSession(request: InlineCompletionRequest): Boolean {
    val session = currentSession
    if (session == null) {
      return false
    }
    if (session.provider.restartOn(request.event)) {
      invalidate(session, request.event)
      return false
    }

    val result = updateSession(session, request)
    onUpdate(session, request.event, result)
    return result != UpdateSessionResult.Invalidated
  }

  private fun invalidate(session: InlineCompletionSession, event: InlineCompletionEvent) {
    onUpdate(session, event, UpdateSessionResult.Invalidated)
  }

  private fun updateSession(session: InlineCompletionSession, request: InlineCompletionRequest): UpdateSessionResult {
    if (request.event.mayMutateCaretPosition()) {
      session.context.expectedStartOffset = request.endOffset
    }

    val provider = session.provider
    val overtyper = provider.overtyper
    // Preserving back compatibility
    val updateManager = if (overtyper::class != DefaultInlineCompletionOvertyper::class) overtyper else provider.suggestionUpdateManager
    return updateSession(session, updateManager, request)
  }

  private fun updateSession(
    session: InlineCompletionSession,
    suggestionUpdateManager: InlineCompletionSuggestionUpdateManager,
    request: InlineCompletionRequest,
  ): UpdateSessionResult {
    val event = request.event

    if (!session.isActive()) { // variants are not provided yet
      return when (suggestionUpdateManager.updateWhileNoVariants(event)) {
        true -> UpdateSessionResult.Succeeded
        false -> UpdateSessionResult.Invalidated
      }
    }

    val success = session.update(event) { variant -> suggestionUpdateManager.update(event, variant) }
    if (!success) {
      return UpdateSessionResult.Invalidated
    }

    check(!session.context.isDisposed)
    if (!session.context.textToInsert().isEmpty()) {
      return UpdateSessionResult.Succeeded
    }

    val snapshot = session.capture() ?: return UpdateSessionResult.Emptied
    val variantState = snapshot.activeVariant.state
    return when (variantState) {
      InlineCompletionVariant.Snapshot.State.COMPUTED -> UpdateSessionResult.Emptied
      InlineCompletionVariant.Snapshot.State.INVALIDATED -> error("Incorrect state: variant cannot be invalidated.")
      else -> UpdateSessionResult.Succeeded
    }
  }

  /**
   * This method returns `true` for the events that may change the expected caret position. Others cannot.
   *
   * See [IJPL-160342](https://youtrack.jetbrains.com/issue/IJPL-160342/Inline-Completion-is-not-removed-after-selecting-previous-word)
   */
  private fun InlineCompletionEvent.mayMutateCaretPosition(): Boolean {
    return this !is InlineCompletionEvent.InlineLookupEvent
  }

  protected enum class UpdateSessionResult {
    Succeeded,
    Invalidated,
    Emptied
  }
}
