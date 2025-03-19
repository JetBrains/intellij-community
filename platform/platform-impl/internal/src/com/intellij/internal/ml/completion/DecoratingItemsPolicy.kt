// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml.completion

interface DecoratingItemsPolicy {
  companion object {
    val DISABLED: DecoratingItemsPolicy = object : DecoratingItemsPolicy {
      override fun itemsToDecorate(scores: Iterable<Double>): Set<Int> = emptySet()
    }
  }

  fun itemsToDecorate(scores: Iterable<Double>): Set<Int>

  class ByAbsoluteThreshold(private val threshold: Double) : DecoratingItemsPolicy {
    override fun itemsToDecorate(scores: Iterable<Double>): Set<Int> {
      scores.firstOrNull()?.let {
        if (it >= threshold) {
          return setOf(0)
        }
      }
      return emptySet()
    }
  }

  class ByRelativeThreshold(private val threshold: Double) : DecoratingItemsPolicy {
    override fun itemsToDecorate(scores: Iterable<Double>): Set<Int> {
      val topItems = scores.take(2)
      if (topItems.size == 1 || (topItems.size >= 2 && topItems[0] - topItems[1] >= threshold)) {
        return setOf(0)
      }
      return emptySet()
    }
  }

  class Composite(private vararg val policies: DecoratingItemsPolicy) : DecoratingItemsPolicy {
    override fun itemsToDecorate(scores: Iterable<Double>): Set<Int> {
      if (policies.isEmpty()) {
        return emptySet()
      }
      var result: Set<Int>? = null
      for (policy in policies) {
        if (result == null) {
          result = policy.itemsToDecorate(scores)
        } else {
          result = result.intersect(policy.itemsToDecorate(scores))
        }
        if (result.isEmpty()) {
          return emptySet()
        }
      }
      return result ?: emptySet()
    }
  }
}
