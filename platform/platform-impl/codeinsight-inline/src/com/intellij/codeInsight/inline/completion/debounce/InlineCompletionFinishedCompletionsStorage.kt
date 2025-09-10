// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.debounce

import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
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

  data class FinishedCompletion(val providerId: InlineCompletionProviderID, val result: Result, val finishTime: Long)

  private val completionsPerProvider: MutableMap<InlineCompletionProviderID, ArrayDeque<FinishedCompletion>> = HashMap()

  fun record(provider: InlineCompletionProviderID, result: Result) {
    val now = System.currentTimeMillis()
    cleanupExpired(now)
    val q = completionsPerProvider.getOrPut(provider) { ArrayDeque() }
    q.addLast(FinishedCompletion(provider, result, now))
  }

  /**
   * Returns a snapshot of recent completions with timestamps (most recent at the end) for the given provider.
   */
  fun getRecentCompletions(provider: InlineCompletionProviderID): List<FinishedCompletion> {
    cleanupExpired(System.currentTimeMillis())
    return completionsPerProvider[provider]?.toList().orEmpty()
  }

  private fun cleanupExpired(now: Long) {
    val threshold = now - EXPIRATION_MS
    val it = completionsPerProvider.entries.iterator()
    while (it.hasNext()) {
      val entry = it.next()
      val q = entry.value
      while (true) {
        val first = q.peekFirst() ?: break
        if (first.finishTime < threshold) q.removeFirst() else break
      }
      if (q.isEmpty()) it.remove()
    }
  }

  companion object {
    private const val EXPIRATION_MS: Long = 10 * 60 * 1000 // 10 minutes
    fun getInstance(project: Project): InlineCompletionFinishedCompletionsStorage = project.service()
  }
}
