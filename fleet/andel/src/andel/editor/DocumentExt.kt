// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import andel.intervals.Interval
import andel.operation.*
import andel.text.Text
import andel.text.TextRange
import andel.text.replaceOperation

fun MutableDocument.replaceRange(range: TextRange, replacement: String) {
  edit(text.view().replaceOperation(range.start.toInt(), range.end.toInt(), replacement))
}

fun MutableDocument.replaceAll(replacement: String) {
  edit(text.overwriteOperation(replacement))
}

internal fun MutableDocument.replaceRanges(ranges: List<Pair<TextRange, String>>) {
  edit(composeAll(
    ranges.map { (range, replacement) -> text.view().replaceOperation(range.start.toInt(), range.end.toInt(), replacement) }
  ))
}

fun MutableDocument.deleteRanges(ranges: Collection<TextRange>) {
  edit(text.deleteRangesOperation(ranges))
}

fun Interval<*, *>.toRange(): TextRange = TextRange(from, to)

fun Text.overwriteOperation(replacement: String): Operation {
  // todo: create linediff, get rid of deduce
  return view().replaceOperation(0.toInt(), charCount.toLong().toInt(), replacement, deduce = true)
}

private fun Text.deleteRangesOperation(ranges: Collection<TextRange>): Operation {
  val ops: MutableList<Op> = ArrayList()
  var lastOffset: Long = 0
  val sortedDeletions = ranges.sortedBy { it.start }
  for (deletion in sortedDeletions) {
    ops.add(Op.Retain(deletion.start - lastOffset))
    ops.add(Op.Replace(substring(deletion), ""))
    lastOffset = deletion.end
  }
  ops.add(Op.Retain(charCount.toLong() - lastOffset))
  return Operation(ops)
}

inline fun <T> MutableDocument.withAnchor(offset: Long, sticky: Sticky = Sticky.LEFT, action: (getActualOffset: () -> Long) -> T): T {
  val anchorId = createAnchor(offset, AnchorLifetime.MUTATION, sticky)
  return try {
    action { resolveAnchor(anchorId)!! }
  }
  finally {
    removeAnchor(anchorId)
  }
}