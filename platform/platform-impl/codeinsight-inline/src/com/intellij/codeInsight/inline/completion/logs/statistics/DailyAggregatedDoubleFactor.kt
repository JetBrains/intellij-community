// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import java.time.LocalDate
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator


interface DailyAggregatedDoubleFactor {
  fun availableDays(): List<LocalDate>

  fun onDate(date: LocalDate): Map<String, Double>?
}

interface MutableDoubleFactor : DailyAggregatedDoubleFactor {
  fun incrementOnToday(key: String): Boolean

  fun updateOnDate(date: LocalDate, updater: MutableMap<String, Double>.() -> Unit): Boolean
}

private fun DailyAggregatedDoubleFactor.aggregateBy(reduce: (Double, Double) -> Double): Map<String, Double> {
  val result = mutableMapOf<String, Double>()
  for (onDate in availableDays().mapNotNull(this::onDate)) {
    for ((key, value) in onDate) {
      result.compute(key) { _, old -> if (old == null) value else reduce(old, value) }
    }
  }

  return result
}

fun DailyAggregatedDoubleFactor.aggregateSum(): Map<String, Double> = aggregateBy { d1, d2 -> d1 + d2 }
