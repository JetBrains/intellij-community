// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text.impl

import andel.rope.Metric
import andel.rope.Metrics
import andel.rope.Monoid

internal const val MAX_LEAF_SIZE = 1024
internal const val DESIRED_LEAF_SIZE = MAX_LEAF_SIZE / 2 + MAX_LEAF_SIZE / 4

internal object TextMonoid : Monoid<String>(MAX_LEAF_SIZE) {
  internal val CharsCount: Metric = sumMetric()

  internal val NewlinesCount: Metric = sumMetric()

  override fun measure(data: String): Metrics =
    Metrics(intArrayOf(
      data.length,
      data.count { it == '\n' }
    ))

  override fun merge(leafData1: String, leafData2: String): String =
    leafData1 + leafData2

  override fun split(leaf: String): List<String> =
    leaf.chunked(DESIRED_LEAF_SIZE)

  override fun leafSize(leaf: String): Int =
    leaf.length
}
