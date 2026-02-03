// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.lang.annotation.HighlightSeverity
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max

/**
 * Synchronized storage for error counts.
 * Stores a separate number for each severity and context combination, including `null` context.
 * If a highlighter does not have a context, it's always shown, so errorCount for each context should count it.
 */
@ApiStatus.Internal
class ErrorCountStorage {
  private val errorCount = Object2IntOpenHashMap<HighlightKey>() // guarded by errorCount

  fun getErrorCount(severity: HighlightSeverity, context: CodeInsightContext): Int {
    val contextHighlightKey = HighlightKey(severity, context)
    val noContextHighlightKey = HighlightKey(severity, null) // highlighters without context are always shown
    return synchronized(errorCount) {
      errorCount.getInt(contextHighlightKey) + errorCount.getInt(noContextHighlightKey)
    }
  }

  fun incErrorCount(infoSeverity: HighlightSeverity, context: CodeInsightContext?, delta: Int) {
    val highlightKey = HighlightKey(infoSeverity, context)
    synchronized(errorCount) {
      val oldVal = errorCount.getInt(highlightKey)
      val newVal = max(0, oldVal + delta)
      errorCount.put(highlightKey, newVal)
    }
  }

  fun clear() {
    synchronized(errorCount) {
      errorCount.clear()
    }
  }

  private data class HighlightKey(
    val severity: HighlightSeverity,
    val context: CodeInsightContext?,
  )
}