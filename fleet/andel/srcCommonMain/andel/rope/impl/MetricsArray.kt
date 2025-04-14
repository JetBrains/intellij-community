// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.rope.impl

import andel.rope.Metric
import andel.rope.Metrics
import fleet.fastutil.ints.IntArrayList
import kotlin.jvm.JvmInline
import fleet.fastutil.ints.MutableIntList
import fleet.fastutil.ints.addElements
import fleet.fastutil.ints.toArray

internal fun concatArrays(
  rank: Int,
  left: MetricsArray, leftFrom: Int, leftTo: Int,
  right: MetricsArray, rightFrom: Int, rightTo: Int,
): MetricsArray =
  MetricsArray(concatArrays(left.metrics, leftFrom * rank, leftTo * rank,
                            right.metrics, rightFrom * rank, rightTo * rank))

internal fun concatArrays(
  left: MetricsArray,
  right: MetricsArray,
): MetricsArray =
  MetricsArray(left.metrics + right.metrics)

@JvmInline
internal value class MetricsArray(val metrics: IntArray) {
  companion object {
    fun of(metrics: Metrics): MetricsArray = MetricsArray(metrics.metrics)
    val Empty: MetricsArray = MetricsArray(IntArray(0))
    fun builder(rank: Int, capacity: Int = 32): MetricsArrayBuilder = MetricsArrayBuilder(IntArrayList(capacity * rank))
  }

  fun copy(): MetricsArray = MetricsArray(metrics.copyOf())

  fun set(rank: Int, i: Int, m: Metrics) {
    m.metrics.copyInto(metrics, i * rank)
  }

  fun set(rank: Int, index: Int, from: MetricsArray, fromIndex: Int) {
    from.metrics.copyInto(
      destination = metrics,
      destinationOffset = index * rank,
      startIndex = fromIndex * rank,
      endIndex = fromIndex * rank + rank
    )
  }

  fun get(rank: Int, i: Int): Metrics =
    Metrics(metrics.copyOfRange(i * rank, (i + 1) * rank))

  fun get(rank: Int, i: Int, kind: Metric): Int =
    metrics[i * rank + kind.id]

  fun copyOfRange(rank: Int, from: Int, to: Int): MetricsArray =
    MetricsArray(metrics.copyOfRange(from * rank, to * rank))

  fun removeAt(rank: Int, idx: Int): MetricsArray =
    concatArrays(rank, this, 0, idx, this, idx + 1, size(rank))

  fun size(rank: Int): Int = metrics.size / rank
}

@JvmInline
internal value class MetricsArrayBuilder(val flatList: MutableIntList) {

  fun add(m: Metrics) {
    flatList.addElements(flatList.size, m.metrics)
  }

  fun build(): MetricsArray = MetricsArray(flatList.toArray())
}
