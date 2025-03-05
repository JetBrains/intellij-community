// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

class PrefixLengthReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
  fun getCountsByPrefixLength(): Map<Int, Double> {
    return factor.aggregateSum().asIterable().associate { (key, value) -> key.toInt() to value }
  }

  fun getAveragePrefixLength(): Double? {
    val lengthToCount = getCountsByPrefixLength()
    if (lengthToCount.isEmpty()) return null

    val totalChars = lengthToCount.asSequence().sumOf { it.key * it.value }
    val completionCount = lengthToCount.asSequence().sumOf { it.value }

    if (completionCount == 0.0) return null
    return totalChars / completionCount
  }
}

class PrefixLengthUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
  fun fireCompletionPerformed(prefixLength: Int) {
    factor.incrementOnToday(prefixLength.toString())
  }
}