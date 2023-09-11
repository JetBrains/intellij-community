// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

@ApiStatus.Experimental
abstract class DebouncedInlineCompletionProvider : InlineCompletionProvider {
  private var jobCall: Job? = null
  protected abstract val delay: Duration

  /**
   * Returns a Flow of InlineCompletionElement objects with debounced proposals for the given InlineCompletionRequest.
   * Override [delay] to control debounce delay
   */
  abstract suspend fun getProposalsDebounced(request: InlineCompletionRequest): Flow<InlineCompletionElement>

  /**
   * Forces the inline completion for the given request.
   * Might be useful for direct call, since it does not requires any delays
   *
   * @return `true` if the inline completion need to be forced, `false` otherwise.
   */
  abstract fun force(request: InlineCompletionRequest): Boolean

  override suspend fun getProposals(request: InlineCompletionRequest): Flow<InlineCompletionElement> {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return getProposalsDebounced(request)
    }

    if (force(request)) {
      jobCall?.cancel()
      return getProposalsDebounced(request)
    }

    return debounce(request)
  }

  suspend fun debounce(request: InlineCompletionRequest): Flow<InlineCompletionElement> {
    jobCall?.cancel()
    jobCall = coroutineContext.job
    delay(delay)
    return getProposalsDebounced(request)
  }
}
