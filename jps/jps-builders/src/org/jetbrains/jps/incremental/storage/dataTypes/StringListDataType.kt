// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage.dataTypes

import org.h2.mvstore.DataUtils.readVarInt
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import org.jetbrains.annotations.ApiStatus

import java.nio.ByteBuffer

@ApiStatus.Internal
object StringListDataType : DataType<Array<String>> {
  override fun isMemoryEstimationAllowed(): Boolean = true

  // don't care about non-ASCII for size computation - non-ASCII strings should be quite rare
  override fun getMemory(obj: Array<String>): Int = Int.SIZE_BYTES + obj.sumOf { it.length }

  override fun createStorage(size: Int): Array<Array<String>?> = arrayOfNulls(size)

  override fun write(buff: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Array<String>>
    for (l in storage) {
      buff.putVarInt(l.size)
      for (s in l) {
        val bytes = s.toByteArray()
        buff.putVarInt(bytes.size).put(bytes)
      }
    }
  }

  override fun write(buff: WriteBuffer, obj: Array<String>) {
    throw IllegalStateException("Must not be called")
  }

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Array<String>>
    for (i in 0 until len) {
      storage[i] = Array(readVarInt(buff)) {
        val bytes = ByteArray(readVarInt(buff))
        buff.get(bytes)
        String(bytes)
      }
    }
  }

  override fun read(buff: ByteBuffer): Array<String> {
    throw IllegalStateException("Must not be called")
  }

  override fun compare(a: Array<String>, b: Array<String>): Int = throw IllegalStateException("Must not be called")

  override fun binarySearch(key: Array<String>?, storage: Any?, size: Int, initialGuess: Int): Int {
    throw IllegalStateException("Must not be called")
  }
}