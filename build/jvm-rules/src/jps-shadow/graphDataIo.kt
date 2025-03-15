@file:Suppress("SSBasedInspection")

package org.jetbrains.jps.dependency

import androidx.collection.MutableScatterMap
import org.jetbrains.bazel.jvm.emptyList
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

interface GraphDataInput : DataInput {
  @Throws(IOException::class)
  fun <T : ExternalizableGraphElement> readGraphElement(): T

  @Throws(IOException::class)
  fun <T : ExternalizableGraphElement, C : MutableCollection<in T>> readGraphElementCollection(result: C): C

  fun readRawLong(): Long

  fun readStringList(): List<String> {
    return readList { readUTF() }
  }

  override fun readLine(): String = throw UnsupportedOperationException()
}

interface GraphDataOutput : DataOutput {
  fun <T : ExternalizableGraphElement> writeGraphElement(element: T)

  fun <T : ExternalizableGraphElement> writeGraphElementCollection(elementType: Class<out T>, collection: Iterable<T>)

  fun writeRawLong(v: Long)

  override fun writeBytes(s: String) = throw UnsupportedOperationException("do not use")

  override fun writeChars(s: String) = throw UnsupportedOperationException("do not use")

  override fun write(v: Int) {
    writeInt(v)
  }

  fun writeUsages(usages: Collection<Usage>) {
    val totalSize = usages.size
    when (totalSize) {
      0 -> {
        writeInt(0)
      }
      1 -> {
        writeInt(1)
        val usage = usages.single()
        writeGraphElementCollection(usage.javaClass, listOf(usage))
      }
      else -> {
        val classToItem = MutableScatterMap<Class<out Usage>, MutableList<Usage>>()
        for (usage in usages) {
          classToItem.compute(usage.javaClass) { k, v -> (v ?: ArrayList()).also { it.add(usage) } }
        }

        writeInt(classToItem.size)
        classToItem.forEach { k, v ->
          writeGraphElementCollection(k, v)
        }
      }
    }
  }
}

inline fun <reified T : Any> GraphDataInput.readList(reader: GraphDataInput.() -> T): List<T> {
  val size = readInt()
  if (size == 0) {
    return emptyList()
  }

  return Array(size) {
    reader()
  }.asList()
}

internal inline fun <T : Any> GraphDataOutput.writeCollection(collection: Collection<T>, writer: GraphDataOutput.(T) -> Unit) {
  writeInt(collection.size)
  for (t in collection) {
    writer(t)
  }
}