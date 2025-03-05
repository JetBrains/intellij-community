// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.operation

import andel.editor.CaretPosition
import andel.editor.Document
import andel.editor.MutableDocument
import andel.intervals.Intervals
import andel.text.TextRange

data class CapturedOperation(val operation: Operation,
                             val base: EditLog
)

@Deprecated("Consider using rebaseOrNull since rebase could throw IllegalStateException and destroy your worker")
fun CapturedOperation.rebase(document: Document): Operation {
  val arrow = bridge(base, document.edits)
  return operation.transform(arrow, direction = Sticky.LEFT)
}

fun CapturedOperation.rebaseOrNull(document: Document): Operation? {
  val arrow = bridgeOrNull(base, document.edits)
  return arrow?.let { operation.transform(arrow, direction = Sticky.LEFT) }
}

fun CapturedOperation.update(document: Document): CapturedOperation {
  return document.captureOperation(this.rebase(document))
}

/**
 * @return true, if diff was successfully applied, false otherwise.
 */
fun MutableDocument.applyDiff(capturedOperation: CapturedOperation): Boolean {
  val operation = capturedOperation.rebaseOrNull(this)
  return if (operation != null) {
    edit(operation)
    true
  }
  else false
}

data class CapturedOffset(val offset: Long,
                          val base: EditLog
)

@Deprecated("Consider using rebaseOrNull since rebase could throw IllegalStateException and destroy your worker")
fun CapturedOffset.rebase(document: Document): Long {
  val arrow = bridge(base, document.edits)
  return offset.transformOnto(arrow, Sticky.LEFT)
}

fun CapturedOffset.rebaseOrNull(document: Document, direction: Sticky = Sticky.LEFT): Long? {
  val arrow = bridgeOrNull(base, document.edits) ?: return null
  return offset.transformOnto(arrow, direction)
}

data class CapturedTextRange(val range: TextRange,
                             val base: EditLog
)

/**
 * @param emptyRangeStickiness indicates preferred stickiness for the case of empty Range.
 *                             Under these circumstances the entire range is represented as a single offset.
 */
fun CapturedTextRange.rebase(document: Document, emptyRangeStickiness: Sticky? = null): TextRange {
  val arrow = bridge(base, document.edits)
  return range.transformOnto(arrow, Sticky.LEFT, emptyRangeStickiness)
}

fun CapturedTextRange.rebaseOrNull(document: Document): TextRange? {
  val arrow = bridgeOrNull(base, document.edits) ?: return null
  return range.transformOnto(arrow, Sticky.LEFT)
}

data class CapturedIntervals<K, V>(val intervals: Intervals<K, V>,
                                   val base: EditLog
)

fun <K, V> CapturedIntervals<K, V>.rebase(document: Document): Intervals<K, V> {
  val arrow = bridge(base, document.edits)
  return intervals.edit(arrow)
}

data class CapturedCaretPosition(val caretPosition: CaretPosition,
                                 val base: EditLog
)

fun Document.captureCaretPosition(caretPosition: CaretPosition): CapturedCaretPosition {
  return CapturedCaretPosition(base = edits,
                               caretPosition = caretPosition)
}

fun CapturedCaretPosition.rebase(document: Document): CaretPosition {
  val arrow = bridge(base, document.edits)
  return caretPosition.transformOnto(arrow, Sticky.LEFT)
}

private fun Document.requireTimestamp(timestamp: Long) {
  require(this.timestamp >= timestamp) { "document timestamp: ${this.timestamp} < timestamp: ${timestamp}" }
}

fun Document.captureOperation(operation: Operation, timestamp: Long = this.timestamp): CapturedOperation {
  requireTimestamp(timestamp)
  return CapturedOperation(operation = operation,
                           base = edits.asOf(timestamp))
}

fun Document.captureOffset(offset: Long, timestamp: Long = this.timestamp): CapturedOffset {
  requireTimestamp(timestamp)
  return CapturedOffset(offset, edits.asOf(timestamp))
}

fun Document.captureTextRange(range: TextRange, timestamp: Long = this.timestamp): CapturedTextRange {
  requireTimestamp(timestamp)
  return CapturedTextRange(range, edits.asOf(timestamp))
}

fun <K, V> Document.captureIntervals(intervals: Intervals<K, V>, timestamp: Long = this.timestamp): CapturedIntervals<K, V> {
  requireTimestamp(timestamp)
  return CapturedIntervals(intervals, edits.asOf(timestamp))
}

fun bridge(fromLog: EditLog, toLog: EditLog): Operation {
  val (toBefore, toAfter) = v(fromLog, toLog).getOrThrow()
  return toBefore.invert().compose(toAfter).normalizeHard()
}

private fun bridgeOrNull(fromLog: EditLog, toLog: EditLog): Operation? {
  val (toBefore, toAfter) = v(fromLog, toLog).getOrNull() ?: return null
  return toBefore.invert().compose(toAfter).normalizeHard()
}


private fun v(logBefore: EditLog, logAfter: EditLog): Result<Pair<Operation, Operation>> {
  if (logBefore.isEmpty() || logAfter.isEmpty()) {
    if (logBefore.isTrimmed || logAfter.isTrimmed) {
      return Result.failure(IllegalStateException("Could not build operation to represent trimmed EditLog"))
    }
    return Result.success(logBefore.operations.compose() to logAfter.operations.compose())
  }
  var i = logBefore.timestamp - 1
  var j = logAfter.timestamp - 1
  var found = false
  while (i >= 0 && j >= 0 && !found) {
    val iId = logBefore.idAtTimestamp(i).getOrElse { return Result.failure(it) }
    val jId = logAfter.idAtTimestamp(j).getOrElse { return Result.failure(it) }
    found = when {
      iId == jId -> true
      i < j && j > 0 -> {
        j--
        false
      }
      else -> {
        i--
        false
      }
    }
  }
  if (!found) {
    i = -1
    j = -1
  }
  val toBefore = logBefore.slice(i + 1, logBefore.timestamp).compose()
  val toAfter = logAfter.slice(j + 1, logAfter.timestamp).compose()
  return Result.success(toBefore to toAfter)
}
