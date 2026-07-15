// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.operation

import andel.editor.CaretPosition
import andel.editor.Document
import andel.editor.MutableDocument
import andel.intervals.Intervals
import andel.text.TextRange
import kotlinx.serialization.Serializable

data class CapturedOperation(
  val operation: Operation,
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

@Serializable
data class CapturedOffset(
  val offset: Long,
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

@Serializable
data class CapturedTextRange(
  val range: TextRange,
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

data class CapturedIntervals<K, V>(
  val intervals: Intervals<K, V>,
  val base: EditLog
)

fun <K, V> CapturedIntervals<K, V>.rebase(document: Document): Intervals<K, V> {
  val arrow = bridge(base, document.edits)
  return intervals.edit(arrow)
}

@Serializable
data class CapturedCaretPosition(
  val caretPosition: CaretPosition,
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

fun bridgeOrNull(fromLog: EditLog, toLog: EditLog): Operation? {
  val (toBefore, toAfter) = v(fromLog, toLog).getOrNull() ?: return null
  return toBefore.invert().compose(toAfter).normalizeHard()
}

data class EditLogsBase(
  val tsBefore: Long,
  val tsAfter: Long,
)

/**
 * Finds the deepest timestamps at which [logBefore] and [logAfter] contain the same operation *instance* — the point up to
 * which both logs are identical. Everything above it (`tsBefore`/`tsAfter` exclusive) diverges: appended, rebased, or
 * dropped between the two snapshots. Returns `null` when the logs share nothing.
 *
 * The scan runs from the tails downward, so its cost is proportional to the divergent suffixes, not to the accumulated
 * log size.
 *
 * **The scan compares identities ([EditLog.identityAtTimestamp]), NOT the stable operation ids ([EditLog.idAtTimestamp]) — do
 * not "fix" it to use stable ids.** Stable ids survive a rebase: when the rebase loop replays local speculation on top of
 * a new base, the replayed operation keeps its stable id even though its content is OT-transformed and its position
 * shifts. A stable-id scan from the tail would match the replayed speculation itself and treat it as common, hiding the
 * remote operations the rebase inserted below it (e.g. producing an identity [bridge] where a real transform is needed).
 * Identity ids are regenerated on every append, so a replayed instance never matches its pre-rebase self, and a match
 * here guarantees the logs are truly identical up to and including that position.
 */
fun findCommonBase(logBefore: EditLog, logAfter: EditLog): Result<EditLogsBase?> {
  if (logBefore.isEmpty() || logAfter.isEmpty()) {
    if (logBefore.isTrimmed || logAfter.isTrimmed) {
      return Result.failure(IllegalStateException("Could not build operation to represent trimmed EditLog"))
    }
    return Result.success(null)
  }

  var i = logBefore.timestamp - 1
  var j = logAfter.timestamp - 1
  while (i >= 0 && j >= 0) {
    val iId = logBefore.identityAtTimestamp(i).getOrElse { return Result.failure(it) }
    val jId = logAfter.identityAtTimestamp(j).getOrElse { return Result.failure(it) }
    when {
      iId == jId -> {
        return Result.success(EditLogsBase(tsBefore = i, tsAfter = j))
      }
      i < j && j > 0 -> {
        j--
      }
      else -> {
        i--
      }
    }
  }

  return if (logBefore.isTrimmed || logAfter.isTrimmed) {
    Result.failure(IllegalStateException("Could not find a common base of trimmed EditLogs"))
  }
  else {
    Result.success(null)
  }
}

private fun v(logBefore: EditLog, logAfter: EditLog): Result<Pair<Operation, Operation>> {
  return findCommonBase(logBefore, logAfter)
    .map { commonBase ->
      if (commonBase == null) {
        val toBefore = logBefore.operations.compose()
        val toAfter = logAfter.operations.compose()
        toBefore to toAfter
      }
      else {
        val toBefore = logBefore.slice(commonBase.tsBefore + 1, logBefore.timestamp).compose()
        val toAfter = logAfter.slice(commonBase.tsAfter + 1, logAfter.timestamp).compose()
        toBefore to toAfter
      }
    }
}
