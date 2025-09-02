package org.jetbrains.bazel.jvm.mvStore

import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import java.nio.ByteBuffer

private val emptyLongs = arrayOfNulls<Long>(0)

object LongDataType : DataType<Long> {
  override fun getMemory(obj: Long) = Long.SIZE_BYTES

  override fun isMemoryEstimationAllowed(): Boolean = true

  override fun write(buff: WriteBuffer, data: Long) {
    buff.putLong(data)
  }

  override fun write(buff: WriteBuffer, storage: Any?, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Long>
    for (i in 0 until len) {
      buff.putLong(storage[i])
    }
  }

  override fun read(buff: ByteBuffer): Long = buff.getLong()

  override fun read(buff: ByteBuffer, storage: Any?, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Long>
    for (i in 0 until len) {
      storage[i] = buff.getLong()
    }
  }

  override fun createStorage(size: Int): Array<Long?> {
    return if (size == 0) emptyLongs else arrayOfNulls(size)
  }

  override fun compare(one: Long, two: Long): Int = one.compareTo(two)

  override fun binarySearch(keyObj: Long, storageObj: Any, size: Int, initialGuess: Int): Int {
    var high = size - 1
    // the cached index minus one, so that for the first time (when cachedCompare is 0), the default value is used
    var x = initialGuess - 1
    if (x < 0 || x > high) {
      x = high ushr 1
    }
    @Suppress("UNCHECKED_CAST")
    storageObj as Array<Long?>
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