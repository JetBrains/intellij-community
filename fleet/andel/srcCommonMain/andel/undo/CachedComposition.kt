package andel.undo

import andel.operation.EditLog
import andel.operation.Operation
import andel.operation.compose
import andel.operation.normalizeHard

interface CompositionCache {
  val edits: EditLog
  fun computeComposition(fromTimestamp: Long, toTimestamp: Long): Operation {
    return edits.slice(fromTimestamp, toTimestamp).compose().normalizeHard()
  }
}

data class CachedComposition(
  val operation: Operation,
  val fromEdit: Long,
  val toEdit: Long,
)

fun computeCompositionWithCache(
  cache: CachedComposition?,
  edits: EditLog,
  fromTimestamp: Long,
  toTimestamp: Long,
): Pair<CachedComposition, Operation> {
  val result = run {
    when {
      cache != null && fromTimestamp <= cache.fromEdit && toTimestamp >= cache.toEdit -> {
        val head = edits.slice(fromTimestamp, cache.fromEdit).compose()
        val tail = edits.slice(cache.toEdit, toTimestamp).compose()
        listOf(head, cache.operation, tail).compose()
      }
      else -> {
        edits.slice(fromTimestamp, toTimestamp).compose()
      }
    }
  }.normalizeHard()
  return CachedComposition(result, fromTimestamp, toTimestamp) to result
}