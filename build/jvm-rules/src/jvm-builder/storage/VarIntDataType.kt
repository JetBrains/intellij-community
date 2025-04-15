// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.bazel.jvm.worker.storage

import org.h2.mvstore.DataUtils.readVarInt
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import java.nio.ByteBuffer

private val emptyInts = arrayOfNulls<Int>(0)

internal object VarIntDataType : DataType<Int> {
  override fun getMemory(obj: Int) = Int.SIZE_BYTES

  override fun isMemoryEstimationAllowed(): Boolean = true

  override fun write(buff: WriteBuffer, data: Int) {
    buff.putVarInt(data)
  }

  override fun write(buff: WriteBuffer, storage: Any?, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Int>
    for (i in 0 until len) {
      buff.putVarInt(storage[i])
    }
  }

  override fun read(buff: ByteBuffer): Int = readVarInt(buff)

  override fun read(buff: ByteBuffer, storage: Any?, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Int>
    for (i in 0 until len) {
      storage[i] = readVarInt(buff)
    }
  }

  override fun createStorage(size: Int) = if (size == 0) emptyInts else arrayOfNulls(size)

  override fun compare(one: Int, two: Int): Int = one.compareTo(two)

  @Suppress("DuplicatedCode")
  override fun binarySearch(keyObj: Int, storageObj: Any, size: Int, initialGuess: Int): Int {
    var high = size - 1
    // the cached index minus one, so that for the first time (when cachedCompare is 0), the default value is used
    var x = initialGuess - 1
    if (x < 0 || x > high) {
      x = high ushr 1
    }
    @Suppress("UNCHECKED_CAST")
    storageObj as Array<Int?>
    var low = 0
    while (low <= high) {
      val midVal = storageObj[x]!!
      when {
        keyObj > midVal -> low = x + 1
        keyObj < midVal -> high = x - 1
        else -> return x
      }
      x = (low + high) ushr 1
    }
    return -(low + 1)
  }
}