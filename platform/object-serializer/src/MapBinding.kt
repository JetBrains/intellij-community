// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonType
import com.intellij.util.ArrayUtil
import java.lang.reflect.Type
import java.util.*

internal class MapBinding(keyType: Type, valueType: Type, context: BindingInitializationContext) : Binding {
  private val keyBinding = createElementBindingByType(keyType, context)
  private val valueBinding = createElementBindingByType(valueType, context)

  private val isKeyComparable = Comparable::class.java.isAssignableFrom(ClassUtil.typeToClass(keyType))

  override fun serialize(obj: Any, context: WriteContext) {
    val map = obj as Map<*, *>
    val writer = context.writer

    if (context.filter.skipEmptyMap && map.isEmpty()) {
      writer.writeInt(0)
      return
    }

    fun writeEntry(key: Any?, value: Any?, isStringKey: Boolean) {
      if (isStringKey) {
        if (key == null) {
          throw SerializationException("null string keys not supported")
        }
        else {
          writer.setFieldName(key as String)
        }
      }
      else {
        if (key == null) {
          writer.writeNull()
        }
        else {
          keyBinding.serialize(key, context)
        }
      }

      if (value == null) {
        writer.writeNull()
      }
      else {
        valueBinding.serialize(value, context)
      }
    }

    val isStringKey = keyBinding is StringBinding
    writer.stepIn(if (isStringKey) IonType.STRUCT else IonType.LIST)
    if (context.configuration.orderMapEntriesByKeys && isKeyComparable && map !is SortedMap<*, *> && map !is LinkedHashMap<*, *>) {
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
        writeEntry(key, map.get(key), isStringKey)
      }
    }
    else {
      map.forEach { (key, value) -> writeEntry(key, value, isStringKey) }
    }
    writer.stepOut()
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    if (context.reader.type == IonType.NULL) {
      property.set(hostObject, null)
      return
    }

    @Suppress("UNCHECKED_CAST")
    var result = property.readUnsafe(hostObject) as Map<Any?, Any?>?
    if (result != null && ClassUtil.isMutableMap(result)) {
      (result as MutableMap<Any?, Any?>).clear()
    }
    else {
      result = HashMap()
      property.set(hostObject, result)
    }
    readInto(result as MutableMap<Any?, Any?>, context, hostObject)
  }

  override fun deserialize(context: ReadContext, hostObject: Any?): Any {
    val result = HashMap<Any?, Any?>()
    readInto(result, context, hostObject)
    return result
  }

  private fun readInto(result: MutableMap<Any?, Any?>, context: ReadContext, hostObject: Any?) {
    val reader = context.reader

    if (reader.type === IonType.INT) {
      LOG.assertTrue(context.reader.intValue() == 0)
      return
    }

    val isStringKeys = reader.type === IonType.STRUCT
    reader.stepIn()
    while (true) {
      if (isStringKeys) {
        val type = reader.next() ?: break
        val key = reader.fieldName
        val value = read(type, valueBinding, context, hostObject)
        result.put(key, value)
      }
      else {
        val key = read(reader.next() ?: break, keyBinding, context, hostObject)
        val value = read(reader.next() ?: break, valueBinding, context, hostObject)
        result.put(key, value)
      }
    }
    reader.stepOut()
  }

  private fun read(type: IonType, binding: Binding, context: ReadContext, hostObject: Any?): Any? {
    return when (type) {
      IonType.NULL -> null
      else -> binding.deserialize(context, hostObject)
    }
  }
}