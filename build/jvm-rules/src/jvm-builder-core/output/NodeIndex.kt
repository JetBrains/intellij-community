// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker.core.output

import androidx.collection.MutableLongObjectMap
import com.dynatrace.hash4j.hashing.Hashing
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NodeIndex(
  @JvmField val map: MutableLongObjectMap<NodeIndexEntry>,
) {
  fun remove(path: String) {
    map.remove(pathToKey(path.toByteArray()))
  }

  fun getInfo(pathHash: Long): NodeIndexEntry? {
    return map.get(pathHash)
  }

  fun put(pathHash: Long, data: ByteArray, offsetInFile: Long, size: Int) {
    // expect that ABI JAR will be less than 2GB
    val offsetAndSize = offsetInFile shl 32 or (size.toLong() and 0xffffffffL)
    map.put(pathHash, NodeIndexEntry(Hashing.xxh3_64().hashBytesToLong(data), offsetAndSize))
  }

  fun updateOffset(pathHash: Long, offsetInFile: Long, oldNodeIndexEntry: NodeIndexEntry) {
    val offsetAndSize = offsetInFile shl 32 or (oldNodeIndexEntry.size.toLong() and 0xffffffffL)
    map.put(pathHash, oldNodeIndexEntry.copy(offsetAndSize = offsetAndSize))
  }

  fun serializedSize(): Int {
    return Int.SIZE_BYTES + Int.SIZE_BYTES + (map.size * Long.SIZE_BYTES * 3)
  }

  fun write(buffer: ByteBuffer) {
    require(buffer.order() == ByteOrder.LITTLE_ENDIAN)

    buffer.putInt(ABI_IC_NODE_FORMAT_VERSION)

    val keys = LongArray(map.size)
    var index = 0
    map.forEachKey { keys[index++] = it }
    keys.sort()

    buffer.putInt(keys.size)
    buffer.asLongBuffer().also { longBuffer ->
      longBuffer.put(keys)
      buffer.position(buffer.position() + (keys.size * Long.SIZE_BYTES))
    }
    for (key in keys) {
      val entry = map.get(key)!!
      buffer.putLong(entry.digest)
      buffer.putLong(entry.offsetAndSize)
    }
  }
}

internal fun pathToKey(path: ByteArray): Long = Hashing.xxh3_64().hashBytesToLong(path)

fun readNodeIndex(data: ByteBuffer): NodeIndex {
  data.order(ByteOrder.LITTLE_ENDIAN)

  val formatVersion = data.getInt()
  if (formatVersion != ABI_IC_NODE_FORMAT_VERSION) {
    throw RuntimeException("Unsupported ABI IC node format version: $formatVersion")
  }

  val keyCount = data.getInt()
  val longs = LongArray(keyCount * 3)
  data.asLongBuffer().get(longs)
  data.position(data.position() + (longs.size * Long.SIZE_BYTES))

  val map = MutableLongObjectMap<NodeIndexEntry>(keyCount)
  for (i in 0 until keyCount) {
    val key = longs[i]
    val entryDataOffset = keyCount + (i * 2)
    val digest = longs[entryDataOffset]
    val offsetAndSize = longs[entryDataOffset + 1]
    map.put(key, NodeIndexEntry(digest, offsetAndSize))
  }
  return NodeIndex(map)
}

data class NodeIndexEntry(
  @JvmField val digest: Long,
  @JvmField val offsetAndSize: Long,
) {
  val offset: Int
    get() = (offsetAndSize shr 32).toInt()
  val size: Int
    get() = offsetAndSize.toInt()
}