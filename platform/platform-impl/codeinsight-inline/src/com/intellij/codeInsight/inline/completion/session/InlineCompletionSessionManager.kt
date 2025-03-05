// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class InlineCompletionSessionManager(private val editor: Editor) {

  private var currentSession: InlineCompletionSession? = null

  /**
   * Updates [session] according to [result].
   *
   * @see invalidate
   * @see updateSession
   */
  @RequiresEdt
  protected abstract fun onUpdate(session: InlineCompletionSession, result: UpdateSessionResult)

  protected open fun onCreated(): Unit = Unit

  protected open fun onRemoved(): Unit = Unit

  protected abstract fun getSuggestionUpdater(provider: InlineCompletionProvider): InlineCompletionSuggestionUpdateManager

  @RequiresEdt
  fun createSession(
    provider: InlineCompletionProvider,
    request: InlineCompletionRequest,
    disposable: Disposable,
    specificId: InlineCompletionSessionId?
  ): InlineCompletionSession {
    ThreadingAssertions.assertEventDispatchThread()

    check(currentSession == null) { "Session already exists." }
    val newSession = InlineCompletionSession.init(editor, provider, request, disposable)
    currentSession = newSession
    
    if (specificId != null) newSession.putId(specificId) else newSession.putId()

    onCreated()
    return newSession
  }

  @RequiresEdt
  fun removeSession() {
    ThreadingAssertions.assertEventDispatchThread()
    check(currentSession != null) { "Session does not exist." }
    InlineCompletionSession.remove(editor)
    onRemoved()
    currentSession = null
  }

  /**
   * Calls [onUpdate] with passed [UpdateSessionResult.Invalidated], if session exists.
   */
  @RequiresEdt
  fun invalidate(reason: UpdateSessionResult.Invalidated.Reason) {
    currentSession?.let { session -> onUpdate(session, UpdateSessionResult.Invalidated(reason)) }
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
   * @see InlineCompletionSuggestionUpdateManager
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
    onUpdate(session, result)
    return result !is UpdateSessionResult.Invalidated
  }

  private fun invalidate(session: InlineCompletionSession, event: InlineCompletionEvent) {
    onUpdate(session, UpdateSessionResult.Invalidated(UpdateSessionResult.Invalidated.Reason.Event(event)))
  }

  private fun updateSession(session: InlineCompletionSession, request: InlineCompletionRequest): UpdateSessionResult {
    if (request.event.mayMutateCaretPosition()) {
      session.context.expectedStartOffset = request.endOffset
    }
    val updateManager = getSuggestionUpdater(session.provider)
    return updateSession(session, updateManager, request)
  }

  private fun updateSession(
    session: InlineCompletionSession,
    suggestionUpdateManager: InlineCompletionSuggestionUpdateManager,
    request: InlineCompletionRequest,
  ): UpdateSessionResult {
    val event = request.event

    if (event is InlineCompletionEvent.WithSpecificProvider && session.provider.id != event.providerId) {
      return UpdateSessionResult.Succeeded
    }

    if (!session.isActive()) { // variants are not provided yet
      return when (suggestionUpdateManager.updateWhileNoVariants(event)) {
        true -> UpdateSessionResult.Succeeded
        false -> UpdateSessionResult.Invalidated(UpdateSessionResult.Invalidated.Reason.Event(event))
      }
    }

    val success = session.update(event) { variant -> suggestionUpdateManager.update(event, variant) }
    if (!success) {
      return UpdateSessionResult.Invalidated(UpdateSessionResult.Invalidated.Reason.Event(event))
    }

    check(!session.context.isDisposed)
    if (!session.isEmpty()) {
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

  private fun InlineCompletionSession.isEmpty(): Boolean {
    return context.state.elements.none { it.element !is InlineCompletionSkipTextElement && it.element.text.isNotEmpty() }
  }

  @ApiStatus.Internal
  sealed interface UpdateSessionResult {
    data object Succeeded : UpdateSessionResult

    data object Emptied : UpdateSessionResult

    data class Invalidated(val reason: Reason) : UpdateSessionResult {
      sealed interface Reason {
        class Event(val event: InlineCompletionEvent) : Reason

        data object UnclassifiedDocumentChange : Reason
      }
    }
  }
}
