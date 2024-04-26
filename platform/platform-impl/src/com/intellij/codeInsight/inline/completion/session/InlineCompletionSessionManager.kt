// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.DefaultInlineCompletionOvertyper
import com.intellij.codeInsight.inline.completion.InlineCompletionOvertyper
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
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
   * Otherwise, whether [onUpdate] was called with either [UpdateSessionResult.Succeeded] or [UpdateSessionResult.Emptied], meaning that
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
    val updateManager = if (overtyper::class != DefaultInlineCompletionOvertyper::class) overtyper else provider.suggestionUpdateManager
    return updateSession(session, updateManager, request)
  }

  private fun updateSession(
    session: InlineCompletionSession,
    suggestionUpdateManager: InlineCompletionSuggestionUpdateManager,
    request: InlineCompletionRequest
  ): UpdateSessionResult {
    val event = request.event

    if (!session.isActive()) { // variants are not provided yet
      return when (suggestionUpdateManager.updateWhileNoVariants(event)) {
        true -> UpdateSessionResult.Succeeded
        false -> UpdateSessionResult.Invalidated
      }
    }

    val success = session.update { variant -> suggestionUpdateManager.update(event, variant) }
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

  protected enum class UpdateSessionResult {
    Succeeded,
    Invalidated,
    Emptied
  }
}
