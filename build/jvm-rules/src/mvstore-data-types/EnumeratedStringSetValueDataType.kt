// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.mvStore

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashSetOf
import org.h2.mvstore.DataUtils
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import java.nio.ByteBuffer

private val emptySets = arrayOfNulls<PersistentSet<*>>(0)

fun <T : Any> enumeratedStringSetValueDataType(
  stringEnumerator: StringEnumerator,
  externalizer: EnumeratedStringDataTypeExternalizer<T>,
) : DataType<PersistentSet<T>>{
  return EnumeratedStringSetValueDataType(
    writer = { buffer, item ->
      buffer.putVarInt(stringEnumerator.enumerate(externalizer.getStringId(item)))
    },
    reader = {
      externalizer.create(stringEnumerator.valueOf(DataUtils.readVarInt(it)))
    }
  )
}

fun enumeratedIntSetValueDataType(): DataType<PersistentSet<Int>> {
  return EnumeratedStringSetValueDataType(
    writer = { buffer, item ->
      buffer.putVarInt(item)
    },
    reader = {
      DataUtils.readVarInt(it)
    }
  )
}

private class EnumeratedStringSetValueDataType<T : Any>(
  private val writer: (buffer: WriteBuffer, item: T) -> Unit,
  private val reader: (buffer: ByteBuffer) -> T,
) : DataType<PersistentSet<T>> {
  override fun getMemory(obj: PersistentSet<T>) = obj.size * Int.SIZE_BYTES

  override fun isMemoryEstimationAllowed(): Boolean = true

  override fun write(buffer: WriteBuffer, data: PersistentSet<T>) = throw UnsupportedOperationException()

  override fun write(buffer: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Set<T>>
    for (i in 0..<len) {
      val data = storage[i]
      buffer.putVarInt(data.size)
      for (item in data) {
        writer(buffer, item)
      }
    }
  }

  override fun read(buffer: ByteBuffer): PersistentSet<T> = throw UnsupportedOperationException()

  override fun read(buffer: ByteBuffer, storage: Any, size: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Set<T>>
    for (i in 0..<size) {
      val size = DataUtils.readVarInt(buffer)
      when (size) {
        0 -> {
          storage[i] = persistentHashSetOf()
        }
        1 -> {
          storage[i] = persistentHashSetOf<T>().add(reader(buffer))
        }
        else -> {
          storage[i] = persistentHashSetOf<T>().mutate { builder ->
            repeat(size) {
              builder.add(reader(buffer))
            }
          }
        }
      }
    }
  }

  override fun createStorage(size: Int): Array<PersistentSet<T>?> {
    @Suppress("UNCHECKED_CAST")
    return if (size == 0) emptySets as Array<PersistentSet<T>?> else arrayOfNulls(size)
  }

  override fun compare(one: PersistentSet<T>, two: PersistentSet<T>): Int = throw UnsupportedOperationException()

  override fun binarySearch(keyObj: PersistentSet<T>, storageObj: Any, size: Int, initialGuess: Int): Int = throw UnsupportedOperationException()
}