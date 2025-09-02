// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.mvStore.kotlin

import com.intellij.util.io.DataExternalizer
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashSetOf
import org.h2.mvstore.DataUtils
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import java.nio.ByteBuffer

private val emptySets = arrayOfNulls<PersistentSet<*>>(0)

internal class DataExternalizerSetValueDataType<T : Any>(private val valueExternalizer: DataExternalizer<T>) : DataType<PersistentSet<T>> {
  override fun getMemory(obj: PersistentSet<T>?): Int = 0

  override fun isMemoryEstimationAllowed(): Boolean = true

  override fun write(buffer: WriteBuffer, data: PersistentSet<T>) = throw UnsupportedOperationException()

  override fun write(buffer: WriteBuffer, storage: Any, len: Int) {
    val output = WriteBufferDataOutput(buffer)

    @Suppress("UNCHECKED_CAST")
    storage as Array<Set<T>>
    for (i in 0..<len) {
      val data = storage[i]
      buffer.putVarInt(data.size)
      for (item in data) {
        valueExternalizer.save(output, item)
      }
    }
  }

  override fun read(buff: ByteBuffer): PersistentSet<T> = throw UnsupportedOperationException()

  override fun read(buff: ByteBuffer, storage: Any, size: Int) {
    val input = ByteBufferDataInput(buff)

    @Suppress("UNCHECKED_CAST")
    storage as Array<Set<T>>
    for (i in 0..<size) {
      val size = DataUtils.readVarInt(buff)
      when (size) {
        0 -> {
          storage[i] = persistentHashSetOf()
        }
        1 -> {
          storage[i] = persistentHashSetOf<T>().add(valueExternalizer.read(input))
        }
        else -> {
          storage[i] = persistentHashSetOf<T>().mutate { builder ->
            repeat(size) {
              builder.add(valueExternalizer.read(input))
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