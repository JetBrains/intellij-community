// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.dynatrace.hash4j.hashing.Hashing
import org.h2.mvstore.MVMap.Decision
import org.h2.mvstore.MVMap.DecisionMaker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.incremental.storage.dataTypes.LongListKeyDataType
import org.jetbrains.jps.incremental.storage.dataTypes.LongPairKeyDataType
import org.jetbrains.jps.incremental.storage.dataTypes.stringTo128BitHash

@ApiStatus.Internal
class ExperimentalOutputToTargetMapping(
  storageManager: StorageManager,
) : OutputToTargetMapping {
  private val map = storageManager.openMap("out-to-target-v1", LongPairKeyDataType, LongListKeyDataType)

  override fun removeTargetAndGetSafeToDeleteOutputs(
    outputPaths: Collection<String>,
    currentTargetId: Int,
    srcToOut: SourceToOutputMapping,
  ): Collection<String> {
    val size = outputPaths.size
    if (size == 0) {
      return emptyList()
    }

    srcToOut as ExperimentalSourceToOutputMapping
    val decisionMaker = LongListRemoveItemDecisionMaker(srcToOut.targetHashId)
    val relativizer = srcToOut.relativizer

    val result = ArrayList<String>(size)
    for (outPath in outputPaths) {
      val key = stringTo128BitHash(relativizer.toRelative(outPath, RelativePathType.OUTPUT))
      map.operate(key, null, decisionMaker)
      if (!decisionMaker.outStillUsed) {
        result.add(outPath)
      }
    }
    return result
  }

  override fun removeMappings(outputPaths: Collection<String>, buildTargetId: Int, srcToOut: SourceToOutputMapping) {
    srcToOut as ExperimentalSourceToOutputMapping
    val relativizer = srcToOut.relativizer
    val decisionMaker = LongListRemoveItemDecisionMaker(srcToOut.targetHashId)
    for (outPath in outputPaths) {
      val key = stringTo128BitHash(relativizer.toRelative(outPath, RelativePathType.OUTPUT))
      map.operate(key, null, decisionMaker)
    }
  }

  fun addMappings(normalizeOutputPaths: Array<String>, targetHashId: Long) {
    val decisionMaker = LongListAddItemDecisionMaker(targetHashId)
    for (outPath in normalizeOutputPaths) {
      map.operate(stringTo128BitHash(outPath), null, decisionMaker)
    }
  }

  fun addMapping(normalizeOutputPath: String, targetHashId: Long) {
    val decisionMaker = LongListAddItemDecisionMaker(targetHashId)
    map.operate(stringTo128BitHash(normalizeOutputPath), null, decisionMaker)
  }

  fun removeTarget(targetId: String, targetTypeId: String) {
    val iterator = map.cursor(null)
    val decisionMaker = LongListRemoveItemDecisionMaker(targetToHash(targetId, targetTypeId))
    while (iterator.hasNext()) {
      map.operate(iterator.next(), null, decisionMaker)
    }
  }
}

private class LongListAddItemDecisionMaker(private val toAdd: Long) : DecisionMaker<LongArray>() {
  override fun decide(existingValue: LongArray?, providedValue: LongArray?): Decision {
    return when {
      existingValue == null || existingValue.isEmpty() -> Decision.PUT
      existingValue.contains(toAdd) -> Decision.ABORT
      else -> Decision.PUT
    }
  }

  // it is called when lock is obtained, so, we can assume that we will not lose a new value if appendOutput is called in parallel
  override fun <T : LongArray?> selectValue(existingValue: T?, ignore: T?): T? {
    if (existingValue == null || existingValue.isEmpty()) {
      @Suppress("UNCHECKED_CAST")
      return longArrayOf(toAdd) as T
    }
    else {
      // we checked `contains` in `decide`
      @Suppress("UNCHECKED_CAST")
      return addElementToEnd(existingValue, toAdd) as T?
    }
  }
}

private class LongListRemoveItemDecisionMaker(private val toRemove: Long) : DecisionMaker<LongArray>() {
  private var indexToRemove: Int = -1
  @JvmField
  var outStillUsed = false

  override fun reset() {
    indexToRemove = -1
    outStillUsed = false
  }

  override fun decide(existingValue: LongArray?, ignore: LongArray?): Decision {
    return when {
      existingValue == null -> Decision.ABORT
      // empty value list is not normal, recover - just delete record
      existingValue.isEmpty() -> Decision.REMOVE
      else -> {
        indexToRemove = existingValue.indexOf(toRemove)
        when {
          indexToRemove == -1 -> {
            outStillUsed = true
            Decision.ABORT
          }
          existingValue.size == 1 -> Decision.REMOVE
          else -> {
            outStillUsed = true
            Decision.PUT
          }
        }
      }
    }
  }

  // it is called when lock is obtained, so, we can assume that we will not lose a new value if appendOutput is called in parallel
  override fun <T : LongArray?> selectValue(existingValue: T, providedValue: T?): T {
    assert(indexToRemove != -1)
    @Suppress("UNCHECKED_CAST")
    return removeElementAtIndex(existingValue!!, indexToRemove) as T
  }
}

private fun removeElementAtIndex(old: LongArray, index: Int): LongArray {
  val newSize = old.size - 1
  val result = LongArray(newSize)
  System.arraycopy(old, 0, result, 0, index)
  if (index < newSize) {
    System.arraycopy(old, index + 1, result, index, newSize - index)
  }
  return result
}

private fun addElementToEnd(old: LongArray, element: Long): LongArray {
  val result = LongArray(old.size + 1)
  System.arraycopy(old, 0, result, 0, old.size)
  result[old.size] = element
  return result
}

internal fun targetToHash(targetId: String, targetTypeId: String): Long {
  val b1 = targetId.toByteArray()
  val b2 = targetTypeId.toByteArray()
  return Hashing.xxh3_64().hashStream()
    .putByteArray(b1)
    .putByteArray(b2)
    .asLong
}