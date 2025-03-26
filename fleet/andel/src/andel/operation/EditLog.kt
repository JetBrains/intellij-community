// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:UseSerializers(BifurcanListSerializer::class)

package andel.operation

import fleet.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
class EditLog(val operations: IBifurcanVector<Operation>,
              private val ids: IBifurcanVector<UID>,
              val timestamp: Long = operations.size(),
              private val sumOfOperationSizes: Long = totalOperationSize(operations)
) {

  companion object {
    fun empty(): EditLog {
      return EditLog(BifurcanVector(), BifurcanVector())
    }
  }

  val version: UID?
    get() = ids.nth(ids.size() - 1, null)

  internal val opCount: Long
    get() = operations.size()

  internal val isTrimmed: Boolean
    get() = opCount != timestamp

  fun asOf(timestamp: Long): EditLog {
    val opCount = operations.size()
    val newSize = timestampToOffset(timestamp, opCount)
    return EditLog(operations.slice(0, newSize), ids.slice(0, newSize), timestamp,
                   sumOfOperationSizes - operations.slice(newSize + 1, opCount).sumOf { totalOpSize(it) })
  }

  fun slice(fromTimestamp: Long, toTimestamp: Long): IBifurcanVector<Operation> {
    val opCount = operations.size()
    val from = timestampToOffset(fromTimestamp, opCount)
    val to = timestampToOffset(toTimestamp, opCount)
    return operations.slice(from, to)
  }

  fun asOf(editId: UID?): EditLog {
    return if (editId == null) {
      empty()
    }
    else {
      val foundIdIndex = (opCount - 1 downTo 0).firstOrNull { index ->
        ids.nth(index) == editId
      }

      if (foundIdIndex != null) {
        asOf(offsetToTimestamp(foundIdIndex) + 1)
      }
      else {
        // might happen either due to trimming or another error: we are unable to understand the real position in this case.
        throw IllegalStateException("Could not find anchored editId. Most likely, it refers to a speculated dropped edit.")
      }
    }
  }

  fun operationsSince(editId: UID?): IBifurcanVector<Operation> {
    return if (editId == null) {
      operations
    }
    else {
      val n = ids.size()
      val foundIdIndex = (n - 1 downTo 0).firstOrNull { index ->
        ids.nth(index) == editId
      }

      if (foundIdIndex != null) {
        operations.slice(foundIdIndex + 1, n)
      }
      else {
        // might happen either due to trimming or another error: we are unable to understand the real position in this case.
        throw IllegalStateException("Could not find anchored editId. Most likely, it refers to a speculated dropped edit.")
      }
    }
  }

  operator fun get(timestamp: Long): Operation =
    operations.nth(timestampToOffset(timestamp))

  fun idAtTimestamp(timestamp: Long): Result<UID> = timestampToOffsetSafe(timestamp).map { ids.nth(it) }

  fun append(id: UID, operation: Operation): EditLog {
    if (!operation.isEmpty && !operations.isEmpty() && !operations.last().isEmpty) {
      val opLenBefore = operation.lenBefore
      val logLenAfter = operations.last().lenAfter
      require(opLenBefore == logLenAfter) {
        "trying to add non-composable operation $operation to the log, last op: ${operations.last()}"
      }
    }

    return EditLog(operations = operations.addLast(operation),
                   ids = ids.addLast(id),
                   timestamp = timestamp + 1,
                   sumOfOperationSizes + totalOpSize(operation))
  }

  fun trim(maxEntryCount: Int = 100, maxOpSize: Long = 1_000_000): EditLog {
    val opCount = operations.size()
    if (opCount <= maxEntryCount && maxOpSize <= sumOfOperationSizes)
      return this

    var currentCount = opCount
    var currentSize = sumOfOperationSizes
    var offset = 0L

    while (offset < opCount && currentCount > maxEntryCount || currentSize > maxOpSize) {
      currentSize -= totalOpSize(operations.nth(offset))
      currentCount--
      offset++
    }

    return EditLog(operations.slice(offset, opCount), ids.slice(offset, opCount), timestamp, currentSize)
  }

  fun isEmpty(): Boolean =
    operations.isEmpty()

  val operationsForTests: IBifurcanVector<Operation>
    get() = operations

  private fun offsetToTimestamp(offset: Long, opCount: Long = operations.size()): Long {
    return (offset - opCount) + timestamp
  }

  private fun timestampToOffset(timestamp: Long, opCount: Long = operations.size()): Long {
    return timestampToOffsetSafe(timestamp, opCount).getOrThrow()
  }

  private fun timestampToOffsetSafe(timestamp: Long, opCount: Long = operations.size()): Result<Long> {
    if (timestamp > this.timestamp) {
      throw IllegalArgumentException("Can't asOf into the future: this.timestamp=${this.timestamp}, timestamp=$timestamp")
    }
    val newSize = opCount - (this.timestamp - timestamp)
    if (newSize < 0) {
      return Result.failure(IllegalArgumentException("asOf too far in the past: opCount=$opCount, end up at offset $newSize"))
    }
    return Result.success(newSize)
  }
}

private fun totalOperationSize(operations: IBifurcanVector<Operation>): Long {
  return operations.sumOf { op -> totalOpSize(op) }
}

private fun totalOpSize(op: Operation): Long {
  return op.ops.sumOf { maxOf(it.lenAfter, it.lenBefore) }
}

