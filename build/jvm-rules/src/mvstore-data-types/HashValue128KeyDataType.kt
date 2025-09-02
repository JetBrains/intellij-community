// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.bazel.jvm.mvStore

import com.dynatrace.hash4j.hashing.HashValue128
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import java.nio.ByteBuffer

private val emptyHashes = arrayOfNulls<HashValue128>(0)

object HashValue128KeyDataType : DataType<HashValue128> {
  override fun isMemoryEstimationAllowed(): Boolean = true

  override fun getMemory(obj: HashValue128): Int = 2 * Long.SIZE_BYTES

  override fun createStorage(size: Int): Array<HashValue128?> = if (size == 0) emptyHashes else arrayOfNulls(size)

  override fun write(buff: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    for (value in (storage as Array<HashValue128>)) {
      buff.putLong(value.mostSignificantBits)
      buff.putLong(value.leastSignificantBits)
    }
  }

  override fun write(buff: WriteBuffer, obj: HashValue128): Unit = throw IllegalStateException("Must not be called")

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<HashValue128>
    for (i in 0 until len) {
      storage[i] = HashValue128(buff.getLong(), buff.getLong())
    }
  }

  override fun read(buff: ByteBuffer): HashValue128 = throw IllegalStateException("Must not be called")

  override fun binarySearch(key: HashValue128, storage: Any, size: Int, initialGuess: Int): Int {
    @Suppress("UNCHECKED_CAST")
    storage as Array<HashValue128>

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
        key.mostSignificantBits > b.mostSignificantBits -> 1
        key.mostSignificantBits < b.mostSignificantBits -> -1
        key.leastSignificantBits > b.leastSignificantBits -> 1
        key.leastSignificantBits < b.leastSignificantBits -> -1
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
  override fun compare(a: HashValue128, b: HashValue128): Int {
    return when {
      a.mostSignificantBits > b.mostSignificantBits -> 1
      a.mostSignificantBits < b.mostSignificantBits -> -1
      a.leastSignificantBits > b.leastSignificantBits -> 1
      a.leastSignificantBits < b.leastSignificantBits -> -1
      else -> 0
    }
  }
}

