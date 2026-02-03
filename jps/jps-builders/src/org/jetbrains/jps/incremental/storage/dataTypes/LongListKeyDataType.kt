// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage.dataTypes

import org.h2.mvstore.DataUtils.readVarInt
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer

@ApiStatus.Internal
object LongListKeyDataType : DataType<LongArray> {
  override fun isMemoryEstimationAllowed(): Boolean = true

  // don't care about non-ASCII strings for memory estimation
  override fun getMemory(obj: LongArray): Int = obj.size * Long.SIZE_BYTES

  override fun createStorage(size: Int): Array<LongArray?> = arrayOfNulls(size)

  override fun write(buff: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    for (value in (storage as Array<LongArray>)) {
      buff.putVarInt(value.size)
      for (item in value) {
        buff.putLong(item)
      }
    }
  }

  override fun write(buff: WriteBuffer, obj: LongArray) {
    throw IllegalStateException("Must not be called")
  }

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<LongArray>
    for (i in 0 until len) {
      storage[i] = LongArray(readVarInt(buff)) {
        buff.getLong()
      }
    }
  }

  override fun read(buff: ByteBuffer): LongArray = throw IllegalStateException("Must not be called")

  override fun binarySearch(key: LongArray, storage: Any, size: Int, initialGuess: Int): Int = throw IllegalStateException("Must not be called")

  @Suppress("DuplicatedCode")
  override fun compare(a: LongArray, b: LongArray): Int = throw IllegalStateException("Must not be called")
}