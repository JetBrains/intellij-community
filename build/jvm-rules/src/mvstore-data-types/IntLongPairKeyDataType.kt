// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.mvStore

import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import java.nio.ByteBuffer

data class IntLong(@JvmField val first: Int, @JvmField val second: Long)

private val emptyIntLongArrays = arrayOfNulls<IntLong>(0)

object IntLongPairKeyDataType : DataType<IntLong> {
  override fun isMemoryEstimationAllowed(): Boolean = true

  override fun getMemory(obj: IntLong): Int = Int.SIZE_BYTES + Long.SIZE_BYTES

  override fun createStorage(size: Int): Array<IntLong?> = if (size == 0) emptyIntLongArrays else arrayOfNulls(size)

  override fun write(buff: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    for (value in (storage as Array<IntLong>)) {
      buff.putInt(value.first)
      buff.putLong(value.second)
    }
  }

  override fun write(buff: WriteBuffer, obj: IntLong): Unit = throw IllegalStateException("Must not be called")

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<IntLong>
    for (i in 0 until len) {
      storage[i] = IntLong(buff.getInt(), buff.getLong())
    }
  }

  override fun read(buff: ByteBuffer): IntLong = throw IllegalStateException("Must not be called")

  override fun binarySearch(key: IntLong, storage: Any, size: Int, initialGuess: Int): Int {
    @Suppress("UNCHECKED_CAST")
    storage as Array<IntLong>

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
        key.first > b.first -> 1
        key.first < b.first -> -1
        key.second > b.second -> 1
        key.second < b.second -> -1
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
  override fun compare(a: IntLong, b: IntLong): Int {
    return when {
      a.first > b.first -> 1
      a.first < b.first -> -1
      a.second > b.second -> 1
      a.second < b.second -> -1
      else -> 0
    }
  }
}