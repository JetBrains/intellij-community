// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization

import com.amazon.ion.IonType
import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap

internal class Int2IntMapBinding : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    val map = obj as Int2IntMap
    val writer = context.writer

    if (context.filter.skipEmptyMap && map.isEmpty()) {
      writer.writeInt(0)
      return
    }

    writer.list {
      writer.writeInt(map.size.toLong())

      if (context.configuration.orderMapEntriesByKeys) {
        val keys = map.keys.toIntArray()
        keys.sort()

        for (key in keys) {
          writer.writeInt(key.toLong())
          writer.writeInt(map.get(key).toLong())
        }
      }
      else {
        val entrySet = map.int2IntEntrySet()
        for (entry in entrySet) {
          writer.writeInt(entry.intKey.toLong())
          writer.writeInt(entry.intValue.toLong())
        }
      }
    }
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    if (context.reader.type == IonType.NULL) {
      property.set(hostObject, null)
      return
    }

    val result = property.readUnsafe(hostObject) as Int2IntMap?
    if (result == null) {
      property.set(hostObject, readMap(context.reader))
      return
    }

    result.clear()

    val reader = context.reader
    if (reader.type === IonType.INT) {
      LOG.assertTrue(reader.intValue() == 0)
    }
    else {
      reader.list {
        reader.next()
        val size = reader.intValue()
        doRead(reader, size, result)
      }
    }
  }

  private fun doRead(reader: ValueReader, size: Int, result: Int2IntMap) {
    for (i in 0 until size) {
      reader.next()
      val k = reader.intValue()
      reader.next()
      val v = reader.intValue()
      result.put(k, v)
    }
  }

  override fun deserialize(context: ReadContext, hostObject: Any?): Any {
    return readMap(context.reader)
  }

  private fun readMap(reader: ValueReader): Int2IntOpenHashMap {
    if (reader.type === IonType.INT) {
      LOG.assertTrue(reader.intValue() == 0)
      return Int2IntOpenHashMap()
    }

    return reader.list {
      reader.next()
      val size = reader.intValue()
      val result = Int2IntOpenHashMap(size)
      doRead(reader, size, result)
      result
    }
  }
}