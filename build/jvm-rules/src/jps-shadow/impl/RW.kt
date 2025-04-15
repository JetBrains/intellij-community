package org.jetbrains.jps.dependency.impl

import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

object RW {
  @JvmStatic
  fun <T : Any> writeCollection(out: DataOutput, collection: Iterable<T>, writable: Writer<T>) {
    val col = if (collection is Collection<*>) collection as Collection<T> else collection.toCollection(ArrayList())
    out.writeInt(col.size)
    for (t in col) {
      writable.write(t)
    }
  }

  @JvmStatic
  fun <T : Any> readCollection(`in`: DataInput, reader: Reader<T>): List<T> {
    val size = `in`.readInt()
    @Suppress("UNCHECKED_CAST")
    return Array<Any>(size) {
      reader.read()
    }.asList() as List<T>
  }

  @JvmStatic
  fun <T : Any, C : MutableCollection<in T>> readCollection(`in`: DataInput, reader: Reader<T>, acc: C): C {
    var size = `in`.readInt()
    while (size-- > 0) {
      acc.add(reader.read())
    }
    return acc
  }

  interface Writer<T : Any> {
    @Throws(IOException::class)
    fun write(obj: T)
  }

  interface Reader<T : Any> {
    @Throws(IOException::class)
    fun read(): T
  }
}