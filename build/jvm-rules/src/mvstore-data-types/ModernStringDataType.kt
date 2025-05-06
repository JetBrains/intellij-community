@file:Suppress("ConvertTwoComparisonsToRangeCheck")

package org.jetbrains.bazel.jvm.mvStore

import org.h2.mvstore.DataUtils
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import java.nio.ByteBuffer

private val emptyStringArray = emptyArray<String?>()

object ModernStringDataType : DataType<String> {
  override fun createStorage(size: Int): Array<String?> = if (size == 0) emptyStringArray else arrayOfNulls(size)

  override fun compare(a: String, b: String): Int = a.compareTo(b)

  override fun binarySearch(key: String, storageObj: Any, size: Int, initialGuess: Int): Int {
    @Suppress("UNCHECKED_CAST")
    val storage = storageObj as Array<String?>
    var low = 0
    var high = size - 1
    // the cached index minus one, so that for the first time (when cachedCompare is 0), the default value is used
    var x = initialGuess - 1
    if (x < 0 || x > high) {
      x = high ushr 1
    }
    while (low <= high) {
      val compare = key.compareTo(storage[x]!!)
      when {
        compare > 0 -> low = x + 1
        compare < 0 -> high = x - 1
        else -> return x
      }
      x = (low + high) ushr 1
    }
    return -(low + 1)
  }

  override fun getMemory(obj: String): Int = 24 + 2 * obj.length

  override fun isMemoryEstimationAllowed(): Boolean = true

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<String>
    for (i in 0 until len) {
      val bytes = ByteArray(DataUtils.readVarInt(buff))
      buff.get(bytes)
      storage[i] = String(bytes)
    }
  }

  override fun read(buff: ByteBuffer): String {
    val bytes = ByteArray(DataUtils.readVarInt(buff))
    buff.get(bytes)
    return String(bytes)
  }

  override fun write(buff: WriteBuffer, s: String) {
    val bytes = s.encodeToByteArray()
    buff.putVarInt(bytes.size).put(bytes)
  }

  override fun write(buff: WriteBuffer, storage: Any?, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<String>
    for (i in 0 until len) {
      val bytes = storage[i].encodeToByteArray()
      buff.putVarInt(bytes.size).put(bytes)
    }
  }
}