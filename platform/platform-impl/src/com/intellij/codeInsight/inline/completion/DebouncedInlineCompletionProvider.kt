// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

abstract class DebouncedInlineCompletionProvider : InlineCompletionProvider {
  private var jobCall: Job? = null
  protected abstract val delay: Duration

  /**
   * Returns a Flow of InlineCompletionElement objects with debounced proposals for the given InlineCompletionRequest.
   * Override [delay] to control debounce delay
   */
  abstract suspend fun getSuggestionDebounced(request: InlineCompletionRequest): InlineCompletionSuggestion

  /**
   * Forces the inline completion for the given request.
   * Might be useful for direct call, since it does not require any delays
   *
   * @return `true` if the inline completion need to be forced, `false` otherwise.
   */
  abstract fun shouldBeForced(request: InlineCompletionRequest): Boolean

  override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return getSuggestionDebounced(request)
    }

    if (shouldBeForced(request)) {
      jobCall?.cancel()
      return getSuggestionDebounced(request)
    }

    return debounce(request)
  }

  suspend fun debounce(request: InlineCompletionRequest): InlineCompletionSuggestion {
    jobCall?.cancel()
    jobCall = coroutineContext.job
    delay(delay)
    return getSuggestionDebounced(request)
  }
}
