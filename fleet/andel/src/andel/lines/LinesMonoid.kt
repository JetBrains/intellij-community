// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.lines

import andel.rope.Metric
import andel.rope.Metrics
import andel.rope.Monoid
import andel.rope.Rope

internal const val MAX_LEAF_SIZE = 64
internal const val DESIRED_LEAF_SIZE = MAX_LEAF_SIZE / 2 + MAX_LEAF_SIZE / 4

object LinesMonoid : Monoid<LineArray>(MAX_LEAF_SIZE) {

    val LengthMetric: Metric = sumMetric()

    val HeightMetric: Metric = sumMetric()

    val CountMetric: Metric = sumMetric()

    val WidthMetric: Metric = maxMetric()

    override fun measure(data: LineArray): Metrics =
        data.metrics().let {
            Metrics(intArrayOf(it.length, it.height, it.count, it.width))
        }

    override fun leafSize(leaf: LineArray): Int =
        leaf.size

    override fun merge(leafData1: LineArray, leafData2: LineArray): LineArray =
        leafData1.merge(leafData2)

    override fun split(leaf: LineArray): List<LineArray> =
        leaf.chunked(DESIRED_LEAF_SIZE)
}

typealias LinesRope = Rope<LineArray>