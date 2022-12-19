// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import org.jetbrains.annotations.ApiStatus.Internal

internal data class FractionState<out T>(
  val fraction: Double,
  val state: T,
)

internal fun totalFraction(completed: Double, updates: Iterable<FractionState<*>>): Double {
  return if (updates.all { it.fraction < 0.0 }) {
    if (completed == 0.0) -1.0 else completed
  }
  else {
    completed + updates.sumOf { it.fraction.coerceAtLeast(0.0) }
  }
}

internal typealias ProgressText = @com.intellij.openapi.util.NlsContexts.ProgressText String

internal data class TextDetails(
  val text: ProgressText?,
  val details: ProgressText?,
) {
  companion object {
    val NULL = TextDetails(null, null)
  }
}

internal fun reduceText(states: List<ProgressText?>): ProgressText? {
  return states.firstNotNullOfOrNull { it }
}

internal fun reduceTextDetails(states: List<TextDetails>): TextDetails? {
  return states.firstOrNull { it.text != null }
         ?: states.firstOrNull { it.details != null }
         ?: states.firstOrNull()
}


@Internal
const val ACCEPTABLE_FRACTION_OVERFLOW: Double = 1.0 / Int.MAX_VALUE
