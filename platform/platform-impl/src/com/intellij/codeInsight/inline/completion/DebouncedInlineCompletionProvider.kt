// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

/**
 * Abstract class representing a debounced inline completion provider.
 *
 * This class provides a base implementation for inline completion providers that debounce completion suggestions based on input events.
 * Subclasses must implement the [getSuggestionDebounced] function to generate the debounced suggestions.
 *
 * Also, subclasses must implement [getDebounceDelay] to configure the delay between an input event and start of computations.
 * Please, do not use [delay] as it will be removed in the next release and [getDebounceDelay] will become `abstract`.
 */
abstract class DebouncedInlineCompletionProvider : InlineCompletionProvider {
  private val jobCall = AtomicReference<Job?>(null)

  @Deprecated(
    message = "Please, use more flexible method: getDebounceDelay. This method is going to be removed soon.",
    replaceWith = ReplaceWith("getDebounceDelay(request)"),
    level = DeprecationLevel.WARNING
  )
  protected open val delay: Duration
    @ScheduledForRemoval
    @Deprecated(
      message = "Please, use more flexible method: getDebounceDelay. This method is going to be removed soon.",
      replaceWith = ReplaceWith("getDebounceDelay(request)"),
      level = DeprecationLevel.WARNING
    )
    get() = throw UnsupportedOperationException("Please, use more flexible method: getDebounceDelay.")

  /**
   * Retrieves the delay duration for debouncing code completion requests.
   * This function gives the time interval for which input events are delayed before
   * the completion suggestions are calculated.
   */
  protected open suspend fun getDebounceDelay(request: InlineCompletionRequest): Duration {
    @Suppress("DEPRECATION")
    return delay
  }

  /**
   * Returns a Flow of InlineCompletionElement objects with debounced proposals for the given InlineCompletionRequest.
   * Override [getDebounceDelay] to control debounce delay
   */
  abstract suspend fun getSuggestionDebounced(request: InlineCompletionRequest): InlineCompletionSuggestion

  /**
   * Forces the inline completion for the given request.
   * Might be useful for direct call, since it does not require any delays
   *
   * @return `true` if the inline completion need to be forced, `false` otherwise.
   */
  @Deprecated(
    message = "Please, use getDebounceDelay(event). This method is going to be removed soon.",
    level = DeprecationLevel.WARNING,
  )
  @ScheduledForRemoval
  open fun shouldBeForced(request: InlineCompletionRequest): Boolean {
    return request.event is InlineCompletionEvent.DirectCall
  }

  override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
    replaceJob()
    if (ApplicationManager.getApplication().isUnitTestMode || request.event is InlineCompletionEvent.DirectCall) {
      return getSuggestionDebounced(request)
    }

    return if (shouldBeForced(request)) getSuggestionDebounced(request) else debounce(request)
  }

  suspend fun debounce(request: InlineCompletionRequest): InlineCompletionSuggestion {
    delay(getDebounceDelay(request))
    return getSuggestionDebounced(request)
  }

  private suspend fun replaceJob() {
    val newJob = coroutineContext.job
    if (jobCall.compareAndSet(newJob, newJob)) {
      return
    }

    newJob.invokeOnCompletion {
      jobCall.compareAndSet(newJob, null)
    }

    while (true) {
      val currentJob = jobCall.get()
      if (jobCall.compareAndSet(currentJob, newJob)) {
        currentJob?.cancelAndJoin()
        return
      }
    }
  }
}
