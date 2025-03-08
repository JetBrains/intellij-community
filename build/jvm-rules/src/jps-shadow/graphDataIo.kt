@file:Suppress("SSBasedInspection")

package org.jetbrains.jps.dependency

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.jetbrains.bazel.jvm.emptyList
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.util.*

interface GraphDataInput : DataInput {
  @Throws(IOException::class)
  fun <T : ExternalizableGraphElement> readGraphElement(): T

  @Throws(IOException::class)
  fun <T : ExternalizableGraphElement, C : MutableCollection<in T>> readGraphElementCollection(result: C): C

  fun readRawLong(): Long

  fun readStringList(): List<String> {
    return readList { readUTF() }
  }
}

interface GraphDataOutput : DataOutput {
  @Throws(IOException::class)
  fun <T : ExternalizableGraphElement> writeGraphElement(element: T)

  @Throws(IOException::class)
  fun <T : ExternalizableGraphElement> writeGraphElementCollection(elementType: Class<out T>, collection: Iterable<T>)

  fun writeRawLong(v: Long)

  override fun writeBytes(s: String) = throw UnsupportedOperationException("do not use")

  override fun writeChars(s: String) = throw UnsupportedOperationException("do not use")

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
        val classToItem = Object2ObjectOpenHashMap<Class<out Usage>, MutableList<Usage>>()
        for (usage in usages) {
          classToItem.computeIfAbsent(usage.javaClass) { ArrayList() }.add(usage)
        }

        writeInt(classToItem.size)
        for (entry in classToItem.object2ObjectEntrySet().fastIterator()) {
          writeGraphElementCollection(entry.key, entry.value)
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