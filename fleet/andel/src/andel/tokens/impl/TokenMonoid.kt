// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.tokens.impl

import andel.rope.Metrics
import andel.rope.Monoid

internal const val MAX_LEAF_SIZE = 128
internal const val DESIRED_LEAF_SIZE = MAX_LEAF_SIZE / 2 + MAX_LEAF_SIZE / 4

internal object TokenMonoid : Monoid<TokenArray>(MAX_LEAF_SIZE) {


  val TokenCount = sumMetric()

  val CharCount = sumMetric()

  val RestartableStateCount = sumMetric()

  val EditCount = sumMetric()

  override fun measure(data: TokenArray): Metrics =
    data.measure().let { tokenArrayMetric ->
      Metrics(intArrayOf(
        data.tokenCount,
        data.charCount,
        tokenArrayMetric.restartableStateCount,
        tokenArrayMetric.editCount
      ))
    }

  override fun leafSize(leaf: TokenArray): Int =
    leaf.tokenCount

  override fun merge(leafData1: TokenArray, leafData2: TokenArray): TokenArray =
    leafData1.concat(leafData2)

  override fun split(leaf: TokenArray): List<TokenArray> =
    leaf.split(DESIRED_LEAF_SIZE)

}