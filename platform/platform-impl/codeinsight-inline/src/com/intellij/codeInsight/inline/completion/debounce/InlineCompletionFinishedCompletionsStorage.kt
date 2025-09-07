// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.debounce

import com.intellij.codeInsight.inline.completion.debounce.InlineCompletionFinishedCompletionsStorage.Companion.EXPIRATION_MS
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Project-level container of recent inline-completions for adaptive debounce tuning.
 *
 * Retains completions for the last [EXPIRATION_MS] milliseconds (10 minutes) and discards older ones.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class InlineCompletionFinishedCompletionsStorage {
  enum class Result { ACCEPTED, REJECTED, OTHER }

  data class FinishedCompletion(val result: Result, val finishTime: Long)

  private val previousCompletions = ArrayDeque<FinishedCompletion>()

  @Synchronized
  fun record(result: Result) {
    val now = System.currentTimeMillis()
    cleanupExpired(now)
    previousCompletions.addLast(FinishedCompletion(result, now))
  }

  /** Returns a snapshot of recent actions with timestamps (most recent at the end). */
  @Synchronized
  fun getRecentCompletions(): List<FinishedCompletion> {
    cleanupExpired(System.currentTimeMillis())
    return ArrayList(previousCompletions)
  }

  private fun cleanupExpired(now: Long) {
    val threshold = now - EXPIRATION_MS
    while (true) {
      val first = previousCompletions.peekFirst() ?: break
      if (first.finishTime < threshold) previousCompletions.removeFirst() else break
    }
  }

  companion object {
    private const val EXPIRATION_MS: Long = 10 * 60 * 1000 // 10 minutes
    fun getInstance(project: Project): InlineCompletionFinishedCompletionsStorage = project.service()
  }
}
