// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

private const val explicitCancelKey = "explicitCancel"
private const val selectedKey = "selected"
private const val invalidatedKey = "invalidated"
private const val otherKey = "other"

class CompletionFinishTypeReader(private val factor: DailyAggregatedDoubleFactor) : FactorReader {
  fun getCountByKey(key: String): Double = factor.aggregateSum()[key] ?: 0.0

  fun getTotalCount(): Double =
    getCountByKey(explicitCancelKey) + getCountByKey(selectedKey) + getCountByKey(invalidatedKey) + getCountByKey(otherKey)
}

class CompletionFinishTypeUpdater(private val factor: MutableDoubleFactor) : FactorUpdater {
  fun fireExplicitCancel(): Boolean = factor.incrementOnToday(explicitCancelKey)
  fun fireSelected(): Boolean = factor.incrementOnToday(selectedKey)
  fun fireInvalidated(): Boolean = factor.incrementOnToday(invalidatedKey)
  fun fireOther(): Boolean = factor.incrementOnToday(otherKey)

}