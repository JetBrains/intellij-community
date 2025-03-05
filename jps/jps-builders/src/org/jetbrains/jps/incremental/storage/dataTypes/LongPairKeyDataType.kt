// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage.dataTypes

import com.dynatrace.hash4j.hashing.Hashing
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer

// getBytes is faster (70k op/s vs. 50 op/s)
// use xxh3_64 as first as it is more proven hash algo than komihash
internal fun stringTo128BitHash(string: String): LongArray {
  val bytes = string.toByteArray()
  return longArrayOf(Hashing.xxh3_64().hashBytesToLong(bytes), Hashing.komihash5_0().hashBytesToLong(bytes))
}

@ApiStatus.Internal
object LongPairKeyDataType : DataType<LongArray> {
  override fun isMemoryEstimationAllowed(): Boolean = true

  // don't care about non-ASCII strings for memory estimation
  override fun getMemory(obj: LongArray): Int = 2 * Long.SIZE_BYTES

  override fun createStorage(size: Int): Array<LongArray?> = arrayOfNulls(size)

  override fun write(buff: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    for (value in (storage as Array<LongArray>)) {
      buff.putLong(value[0])
      buff.putLong(value[1])
    }
  }

  override fun write(buff: WriteBuffer, obj: LongArray): Unit = throw IllegalStateException("Must not be called")

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<LongArray>
    for (i in 0 until len) {
      storage[i] = longArrayOf(buff.getLong(), buff.getLong())
    }
  }

  override fun read(buff: ByteBuffer): LongArray = throw IllegalStateException("Must not be called")

  override fun binarySearch(key: LongArray, storage: Any, size: Int, initialGuess: Int): Int {
    @Suppress("UNCHECKED_CAST")
    storage as Array<LongArray>

    var low = 0
    var high = size - 1
    // the cached index minus one, so that for the first time (when cachedCompare is 0), the default value is used
    var x = initialGuess - 1
    if (x < 0 || x > high) {
      x = high ushr 1
    }
    while (low <= high) {
      val b = storage[x]
      val compare = when {
        key[0] > b[0] -> 1
        key[0] < b[0] -> -1
        key[1] > b[1] -> 1
        key[1] < b[1] -> -1
        else -> 0
      }

      when {
        compare > 0 -> low = x + 1
        compare < 0 -> high = x - 1
        else -> return x
      }
      x = (low + high) ushr 1
    }
    return low.inv()
  }

  @Suppress("DuplicatedCode")
  override fun compare(a: LongArray, b: LongArray): Int {
    return when {
      a[0] > b[0] -> 1
      a[0] < b[0] -> -1
      a[1] > b[1] -> 1
      a[1] < b[1] -> -1
      else -> 0
    }
  }
}