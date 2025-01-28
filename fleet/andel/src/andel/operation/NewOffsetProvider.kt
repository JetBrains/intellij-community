// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.operation

import andel.intervals.Intervals

fun interface NewOffsetProvider {
  fun getNewOffset(offset: Long): Long
}

fun <K, T> Intervals<K, T>.edit(newOffsetProvider: NewOffsetProvider): Intervals<K, T> {
  val ops: MutableList<Op> = ArrayList()
  var currentShift = 0L
  var offset = 0L

  fun processOffset(pointOffset: Long) {
    val toShift: Long = newOffsetProvider.getNewOffset(pointOffset) - pointOffset - currentShift
    currentShift += toShift
    val opOffset = if (toShift > 0) pointOffset else pointOffset - toShift
    ops.add(Op.Retain(opOffset - offset))
    if (toShift < 0) ops.add(Op.Replace(" ".repeat(-toShift.toInt()), ""))
    if (toShift > 0) ops.add(Op.Replace("", " ".repeat(toShift.toInt())))
    offset = pointOffset
  }

  processOffset(0)
  val allOffsets = ArrayList<Long>()
  for (interval in this.asIterable()) {
    if (interval.from - 1 > offset) allOffsets.add(interval.from - 1)
    if (interval.from > offset) allOffsets.add(interval.from)
    if (interval.to > offset) allOffsets.add(interval.to)
  }
  allOffsets.sort()
  for (it in allOffsets) {
    if (it > offset)
      processOffset(it)
  }

  return edit(Operation(ops))
}
