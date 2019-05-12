// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonType
import com.intellij.util.ArrayUtil
import gnu.trove.THashMap
import java.util.*

internal class MapBinding(private val keyClass: Class<*>, valueClass: Class<*>, context: BindingInitializationContext) : RootBinding, NestedBinding {
  private val keyBinding = context.bindingProducer.getRootBinding(keyClass)
  private val valueBinding = context.bindingProducer.getRootBinding(valueClass)

  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    write(hostObject, property, context) {
      serialize(it, context)
    }
  }

  override fun serialize(obj: Any, context: WriteContext) {
    val writer = context.writer
    val map = obj as Map<*, *>

    fun writeEntry(key: Any?, value: Any?) {
      if (key == null) {
        writer.writeNull()
      }
      else {
        keyBinding.serialize(key, context)
      }

      if (value == null) {
        writer.writeNull()
      }
      else {
        valueBinding.serialize(value, context)
      }
    }

    writer.stepIn(IonType.LIST)
    if (context.configuration.orderMapEntriesByKeys && map !is SortedMap<*, *> && map !is LinkedHashMap<*, *> && Comparable::class.java.isAssignableFrom(keyClass)) {
      val keys = ArrayUtil.toObjectArray(map.keys)
      Arrays.sort(keys) { a, b ->
        @Suppress("UNCHECKED_CAST")
        when {
          a == null -> -1
          b == null -> 1
          else -> (a as Comparable<Any>).compareTo(b as Comparable<Any>)
        }
      }
      for (key in keys) {
        writeEntry(key, map.get(key))
      }
    }
    else {
      if (map is THashMap) {
        map.forEachEntry { k: Any?, v: Any? ->
          writeEntry(k, v)
          true
        }
      }
      else {
        map.forEach(::writeEntry)
      }
    }
    writer.stepOut()
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    if (context.reader.type == IonType.NULL) {
      property.set(hostObject, null)
      return
    }

    @Suppress("UNCHECKED_CAST")
    var result = property.read(hostObject) as MutableMap<Any?, Any?>?
    if (result != null && ClassUtil.isMutableMap(result)) {
      result.clear()
    }
    else {
      result = THashMap()
      property.set(hostObject, result)
    }
    readInto(result, context)
  }

  override fun deserialize(context: ReadContext): Any {
    val result = THashMap<Any?, Any?>()
    readInto(result, context)
    return result
  }

  private fun readInto(result: MutableMap<Any?, Any?>, context: ReadContext) {
    val reader = context.reader
    reader.stepIn()
    while (true) {
      val key = read(reader.next() ?: break, keyBinding, context)
      val value = read(reader.next() ?: break, valueBinding, context)
      result.put(key, value)
    }
    reader.stepOut()
  }

  private fun read(type: IonType, binding: RootBinding, context: ReadContext): Any? {
    return when (type) {
      IonType.NULL -> null
      else -> binding.deserialize(context)
    }
  }
}