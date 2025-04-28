package org.jetbrains.bazel.jvm.mvStore

import org.h2.mvstore.DataUtils
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import java.nio.ByteBuffer

interface EnumeratedStringDataTypeExternalizer<T : Any> {
  fun createStorage(size: Int): Array<T?>

  fun create(id: String): T

  fun getStringId(obj: T): String
}

interface StringEnumerator {
  fun enumerate(string: String): Int

  fun valueOf(id: Int): String
}

class EnumeratedStringDataType<T : Any>(
  private val stringEnumerator: StringEnumerator,
  private val externalizer: EnumeratedStringDataTypeExternalizer<T>,
) : DataType<T> {
  override fun getMemory(obj: T) = Int.SIZE_BYTES

  override fun isMemoryEstimationAllowed(): Boolean = true

  override fun write(buff: WriteBuffer, data: T) {
    buff.putVarInt(stringEnumerator.enumerate(externalizer.getStringId(data)))
  }

  override fun write(buff: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<T>
    for (i in 0..<len) {
      write(buff, storage[i])
    }
  }

  override fun read(buff: ByteBuffer): T {
    return externalizer.create(stringEnumerator.valueOf(DataUtils.readVarInt(buff)))
  }

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<T>
    for (i in 0..<len) {
      storage[i] = read(buff)
    }
  }

  override fun createStorage(size: Int) = externalizer.createStorage(size)

  override fun compare(one: T, two: T): Int {
    return externalizer.getStringId(one).compareTo(externalizer.getStringId(two))
  }

  override fun binarySearch(keyObj: T, storageObj: Any, size: Int, initialGuess: Int): Int {
    var high = size - 1
    // the cached index minus one, so that for the first time (when cachedCompare is 0), the default value is used
    var x = initialGuess - 1
    if (x < 0 || x > high) {
      x = high ushr 1
    }
    @Suppress("UNCHECKED_CAST")
    storageObj as Array<T>
    var low = 0
    val key = externalizer.getStringId(keyObj)
    while (low <= high) {
      val compare = key.compareTo(externalizer.getStringId(storageObj[x]))
      when {
        compare > 0 -> low = x + 1
        compare < 0 -> high = x - 1
        else -> return x
      }
      x = (low + high) ushr 1
    }
    return -(low + 1)
  }
}