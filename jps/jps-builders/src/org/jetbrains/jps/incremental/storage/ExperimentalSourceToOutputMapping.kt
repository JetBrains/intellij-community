// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import org.h2.mvstore.MVMap
import org.h2.mvstore.MVMap.Decision
import org.h2.mvstore.MVMap.DecisionMaker
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.dataTypes.LongPairKeyDataType
import org.jetbrains.jps.incremental.storage.dataTypes.StringListDataType

@Internal
class ExperimentalSourceToOutputMapping private constructor(
  mapHandle: MapHandle<LongArray, Array<String>>,
  relativizer: PathRelativizerService,
) : SourceToOutputMapping, ExperimentalOneToManyPathMapping(mapHandle = mapHandle, relativizer = relativizer, valueOffset = 1) {
  companion object {
    // we have a lot of targets - reduce GC and reuse map builder
    private val mapBuilder = MVMap.Builder<LongArray, Array<String>>().also {
      it.setKeyType(LongPairKeyDataType)
      it.setValueType(StringListDataType)
    }

    @JvmStatic
    fun createSourceToOutputMap(
      storageManager: StorageManager,
      relativizer: PathRelativizerService,
      target: BuildTarget<*>,
    ): ExperimentalSourceToOutputMapping {
      return createSourceToOutputMap(
        storageManager = storageManager,
        relativizer = relativizer,
        targetId = target.id,
        targetTypeId = target.targetType.typeId,
      )
    }

    @VisibleForTesting
    @Internal
    fun createSourceToOutputMap(
      storageManager: StorageManager,
      relativizer: PathRelativizerService,
      targetId: String,
      targetTypeId: String,
    ): ExperimentalSourceToOutputMapping {
      // we can use composite key and sort by target id, but as we compile targets in parallel:
      // * avoid blocking - in-memory lock per map root,
      // * avoid a huge B-tree and reduce rebalancing time due to contention.
      return ExperimentalSourceToOutputMapping(
        mapHandle = storageManager.openMap("$targetId|$targetTypeId|src-to-out", mapBuilder),
        relativizer = relativizer,
      )
    }
  }

  //override fun getKey(sourcePath: String): LongArray {
  //  val stringKey = relativizer.toRelative(sourcePath).toByteArray()
  //
  //  // We should sort by target id for data locality -
  //  // the high integer (high) is shifted to the higher 32 bits of the long value,
  //  // while the low integer (low) occupies the lower 32 bits.
  //  // When we do compare two long values, the higher 32 bits (which contain the high integer) dictate the order.
  //  val low = Hashing.komihash5_0().hashBytesToInt(stringKey)
  //  val high = targetId
  //  return longArrayOf((high.toLong() shl 32) or (low.toLong() and 0xFFFFFFFFL), Hashing.xxh3_64().hashBytesToLong(stringKey))
  //}

  override fun setOutput(sourcePath: String, outputPath: String) {
    val relativeSourcePath = relativizer.toRelative(sourcePath)
    mapHandle.map.put(stringTo128BitHash(relativeSourcePath), arrayOf(relativeSourcePath, relativizer.toRelative(outputPath)))
  }

  override fun appendOutput(sourcePath: String, outputPath: String) {
    val relativeSourcePath = relativizer.toRelative(sourcePath)
    mapHandle.map.operate(stringTo128BitHash(relativeSourcePath),
                          null,
                          AddItemDecisionMaker(sourcePath = relativeSourcePath, toAdd = relativizer.toRelative(outputPath)))
  }

  override fun removeOutput(sourcePath: String, outputPath: String) {
    mapHandle.map.operate(getKey(sourcePath), null, RemoveItemDecisionMaker(relativizer.toRelative(outputPath)))
  }

  override fun cursor(): SourceToOutputMappingCursor {
    val cursor = mapHandle.map.cursor(null)
    return object : SourceToOutputMappingCursor {
      override fun hasNext(): Boolean = cursor.hasNext()

      override fun next(): String {
        cursor.next()
        return relativizer.toFull(cursor.value.first<String>())
      }

      override val outputPaths: Array<String>
        get() {
          val list = cursor.value
          return Array<String>(list.size - 1) { relativizer.toFull(list[it + 1]) }
        }
    }
  }

  override fun getSourcesIterator(): Iterator<String> = cursor()
}

private val CHECK_COLLISIONS = System.getProperty("jps.source.to.output.mapping.check.collisions", "false").toBoolean()

private class AddItemDecisionMaker(private val sourcePath: String, private val toAdd: String) : DecisionMaker<Array<String>>() {
  override fun decide(existingValue: Array<String>?, providedValue: Array<String>?): Decision {
    when {
      existingValue == null -> {
        return Decision.PUT
      }
      existingValue.isEmpty() -> {
        if (CHECK_COLLISIONS) {
          throw IllegalStateException("Value is empty")
        }
        return Decision.PUT
      }
      CHECK_COLLISIONS && existingValue[0] != sourcePath -> {
        throw IllegalStateException("Collision for $sourcePath: ${existingValue[0]} and $sourcePath")
      }
      else -> {
        for (i in 1 until existingValue.size) {
          if (existingValue[i] == toAdd) {
            return Decision.ABORT
          }
        }
        return Decision.PUT
      }
    }
  }

  // it is called when lock is obtained, so, we can assume that we will not lose a new value if appendOutput is called in parallel
  override fun <T : Array<String>?> selectValue(existingValue: T?, ignore: T?): T? {
    if (existingValue == null || existingValue.isEmpty()) {
      @Suppress("UNCHECKED_CAST")
      return arrayOf(sourcePath, toAdd) as T
    }
    else {
      // we checked `contains` in `decide`
      @Suppress("UNCHECKED_CAST")
      return addElementToEnd(existingValue, toAdd) as T?
    }
  }
}

private class RemoveItemDecisionMaker(private val toRemove: String) : DecisionMaker<Array<String>>() {
  private var indexToRemove: Int = -1

  override fun decide(existingValue: Array<String>?, ignore: Array<String>?): Decision {
    return when {
      existingValue == null -> Decision.ABORT
      // empty value list is not normal, recover - just delete record
      existingValue.size == 1 -> Decision.REMOVE
      else -> {
        for (i in 1 until existingValue.size) {
          if (existingValue[i] == toRemove) {
            indexToRemove = i
            break
          }
        }
        when {
          indexToRemove == -1 -> Decision.ABORT
          existingValue.size == 2 -> Decision.REMOVE
          else -> Decision.PUT
        }
      }
    }
  }

  // it is called when lock is obtained, so, we can assume that we will not lose a new value if appendOutput is called in parallel
  override fun <T : Array<String>?> selectValue(existingValue: T, providedValue: T?): T {
    assert(indexToRemove != -1)
    @Suppress("UNCHECKED_CAST")
    return removeElementAtIndex(existingValue!!, indexToRemove) as T
  }
}

private fun removeElementAtIndex(old: Array<String>, index: Int): Array<String?> {
  val newSize = old.size - 1
  val result = arrayOfNulls<String>(newSize)
  System.arraycopy(old, 0, result, 0, index)
  if (index < newSize) {
    System.arraycopy(old, index + 1, result, index, newSize - index)
  }
  return result
}

private fun addElementToEnd(old: Array<String>, element: String): Array<String?> {
    val result = arrayOfNulls<String>(old.size + 1)
    System.arraycopy(old, 0, result, 0, old.size)
    result[old.size] = element
    return result
}