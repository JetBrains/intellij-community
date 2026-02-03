// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.rope.impl

import andel.rope.Metric
import andel.rope.Metrics
import andel.rope.Monoid
import kotlin.jvm.JvmInline

@JvmInline
internal value class BitMask(val bitMask: Int) {
  fun getBit(i: Int) = bitMask.and(1.shl(i)) != 0

  fun sum(kind: Metric, lhs: Int, rhs: Int): Int =
    when (getBit(kind.id)) {
      false -> lhs + rhs
      true -> maxOf(lhs, rhs)
    }
  
  fun sum(rank: Int, metrics: MetricsArray): Metrics {
    val dst = IntArray(rank)
    sum(
      rank,
      metrics.metrics, 0, metrics.metrics.size,
      dst, 0)
    return Metrics(dst)
  }
  
  private fun sum(
    rank: Int,
    src: IntArray, from: Int, to: Int,
    dst: IntArray, dstIndex: Int,
  ) {
    var i = from
    while (i < to) {
      var j = 0
      while (j < rank) {
        dst[dstIndex + j] = sum(Metric(j), dst[dstIndex + j], src[i + j])
        j++
      }
      i += rank
    }
  }
  
  fun sum(
    rank: Int,
    lhs: MetricsArray, lhsFrom: Int, lhsTo: Int = lhsFrom + 1,
    dest: MetricsArray, destIndex: Int,
  ) {
    run {
      var i = lhsFrom * rank
      while (i < lhsTo * rank) {
        var kind = 0
        while (kind < rank) {
          dest.metrics[destIndex * rank + kind] = sum(Metric(kind), dest.metrics[destIndex * rank + kind], lhs.metrics[i + kind])
          kind++
        }
        i += rank
      }
    }
  }
  
}

internal fun leafMergeThresh(splitThresh: Int): Int = splitThresh / 2

internal fun <Data> Monoid<Data>.isLeafUnderflown(leafData: Data): Boolean =
  leafSize(leafData) < leafMergeThresh(leafSplitThresh)

internal fun <Data> Monoid<Data>.isLeafOverflown(leafData: Data): Boolean =
  leafSplitThresh < leafSize(leafData)
