package org.jetbrains.bazel.jvm.mvStore

import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashSetOf
import org.h2.mvstore.DataUtils
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import java.nio.ByteBuffer

private val emptySets = arrayOfNulls<Set<*>>(0)

class EnumeratedStringSetValueDataType<T : Any>(
  private val stringEnumerator: StringEnumerator,
  private val externalizer: EnumeratedStringDataTypeExternalizer<T>,
) : DataType<Set<T>> {
  override fun getMemory(obj: Set<T>) = obj.size * Int.SIZE_BYTES

  override fun isMemoryEstimationAllowed(): Boolean = true

  override fun write(buff: WriteBuffer, data: Set<T>) {
    buff.putVarInt(data.size)
    for (item in data) {
      buff.putVarInt(stringEnumerator.enumerate(externalizer.getStringId(item)))
    }
  }

  override fun write(buff: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Set<T>>
    for (i in 0..<len) {
      write(buff, storage[i])
    }
  }

  override fun read(buff: ByteBuffer): Set<T> {
    val size = DataUtils.readVarInt(buff)
    if (size == 0) {
      return persistentHashSetOf()
    }

    return persistentHashSetOf<T>().mutate { builder ->
      repeat(size) {
        builder.add(externalizer.create(stringEnumerator.valueOf(DataUtils.readVarInt(buff))))
      }
    }
  }

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Set<T>>
    for (i in 0..<len) {
      storage[i] = read(buff)
    }
  }

  override fun createStorage(size: Int): Array<Set<T>?> {
    @Suppress("UNCHECKED_CAST")
    return if (size == 0) emptySets as Array<Set<T>?> else arrayOfNulls(size)
  }

  override fun compare(one: Set<T>, two: Set<T>): Int = throw UnsupportedOperationException()

  override fun binarySearch(keyObj: Set<T>, storageObj: Any, size: Int, initialGuess: Int): Int = throw UnsupportedOperationException()
}