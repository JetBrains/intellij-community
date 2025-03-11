package org.jetbrains.jps.dependency.storage

import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashSetOf
import org.h2.mvstore.DataUtils
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.impl.doReadGraphElement
import java.nio.ByteBuffer

private val emptyNodes = arrayOfNulls<Set<Node<*, *>>>(0)

class AnyGraphElementDataType(
  private val stringEnumerator: StringEnumerator,
  private val elementInterner: ((ExternalizableGraphElement) -> ExternalizableGraphElement)?,
) : DataType<Set<Node<*, *>>> {
  override fun getMemory(obj: Set<Node<*, *>>) = 0

  override fun isMemoryEstimationAllowed(): Boolean = false

  override fun write(buff: WriteBuffer, data: Set<Node<*, *>>) {
    buff.putVarInt(data.size)
    val output = WriteBufGraphDataOutput(buff, stringEnumerator)
    for (node in data) {
      output.writeGraphElement(node)
    }
  }

  override fun write(buff: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Set<Node<*, *>>>
    val output = WriteBufGraphDataOutput(buff, stringEnumerator)
    for (i in 0..<len) {
      val data = storage[i]
      buff.putVarInt(data.size)
      for (node in data) {
        output.writeGraphElement(node)
      }
    }
  }

  override fun read(buff: ByteBuffer): Set<Node<*, *>> {
    val size = DataUtils.readVarInt(buff)
    if (size == 0) {
      return persistentHashSetOf()
    }
    return persistentHashSetOf<Node<*, *>>().mutate { builder ->
      val input = WriteBufGraphDataInput(buff, stringEnumerator, elementInterner)
      repeat(size) {
        builder.add(doReadGraphElement(input) { it })
      }
    }
  }

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    if (len == 0) {
      return
    }

    @Suppress("UNCHECKED_CAST")
    storage as Array<Set<Node<*, *>>>
    val input = WriteBufGraphDataInput(buff, stringEnumerator, elementInterner)
    for (i in 0..<len) {
      val size = DataUtils.readVarInt(buff)
      if (size == 0) {
        storage[i] = persistentHashSetOf()
      }
      else {
        storage[i] = persistentHashSetOf<Node<*, *>>().mutate { builder ->
          repeat(size) {
            builder.add(doReadGraphElement(input) { it })
          }
        }
      }
    }
  }

  override fun createStorage(size: Int): Array<Set<Node<*, *>>?> {
    return if (size == 0) emptyNodes else arrayOfNulls(size)
  }

  override fun compare(one: Set<Node<*, *>>, two: Set<Node<*, *>>) = throw UnsupportedOperationException("Not a key")

  override fun binarySearch(keyObj: Set<Node<*, *>>, storageObj: Any, size: Int, initialGuess: Int): Int {
    throw UnsupportedOperationException("Not a key")
  }
}