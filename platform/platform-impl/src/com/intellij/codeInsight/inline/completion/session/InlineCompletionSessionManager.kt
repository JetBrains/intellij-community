// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
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
   * Otherwise, whether [onUpdate] was called with either [UpdateSessionResult.Same] or [UpdateSessionResult.Changed], meaning that
   * the update succeeded.
   */
  @RequiresEdt
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
    val result = updateContext(session.context, request)
    onUpdate(session, result)
    return result !is UpdateSessionResult.Invalidated
  }

  private fun invalidate(session: InlineCompletionSession) = onUpdate(session, UpdateSessionResult.Invalidated)

  private fun updateContext(
    context: InlineCompletionContext,
    request: InlineCompletionRequest
  ): UpdateSessionResult {
    return when (request.event) {
      is InlineCompletionEvent.DocumentChange -> {
        check(request.event.event.oldLength == 0) { "Unsupported document event: ${request.event.event}" }
        val fragment = request.event.event.newFragment.toString()
        applyPrefixAppend(context, fragment, request) ?: UpdateSessionResult.Invalidated
      }
      is InlineCompletionEvent.InlineLookupEvent -> {
        if (context.isCurrentlyDisplaying()) UpdateSessionResult.Same else UpdateSessionResult.Invalidated
      }
      else -> UpdateSessionResult.Invalidated
    }
  }

  private fun applyPrefixAppend(
    context: InlineCompletionContext,
    fragment: String,
    reason: InlineCompletionRequest
  ): UpdateSessionResult.Changed? {
    // only one symbol is permitted
    if (fragment.length != 1 || !context.textToInsert().startsWith(fragment) || context.textToInsert() == fragment) {
      return null
    }
    val truncateTyping = fragment.length
    val newElements = truncateElementsPrefix(context.state.elements.map { it.element }, truncateTyping)
    return newElements?.let { UpdateSessionResult.Changed(it, truncateTyping, reason) }
  }

  private fun truncateElementsPrefix(elements: List<InlineCompletionElement>, length: Int): List<InlineCompletionElement>? {
    var currentLength = length
    val newFirstElementIndex = elements.indexOfFirst {
      currentLength -= it.text.length
      currentLength <= 0 // Searching for the element that reaches [length]
    }
    check(newFirstElementIndex >= 0)
    val firstElement = elements[newFirstElementIndex]
    currentLength += firstElement.text.length
    if (firstElement !is InlineCompletionGrayTextElement) {
      // will be fixed when a prefix truncator appears
      return null
    }
    val newFirstElement = firstElement.withTruncatedPrefix(currentLength)
    return listOfNotNull(newFirstElement) + elements.drop(newFirstElementIndex + 1).map { it.withSameContent() }
  }

  protected sealed interface UpdateSessionResult {
    class Changed(
      val newElements: List<InlineCompletionElement>,
      val truncateTyping: Int,
      val reason: InlineCompletionRequest
    ) : UpdateSessionResult

    data object Same : UpdateSessionResult

    data object Invalidated : UpdateSessionResult
  }
}
