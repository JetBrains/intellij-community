// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonType
import com.intellij.util.ArrayUtil

internal class ArrayBinding(private val itemClass: Class<*>,
                            context: BindingInitializationContext) : BaseCollectionBinding(itemClass, context) {
  override fun deserialize(context: ReadContext, hostObject: Any?) = readArray(context, hostObject)

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    val type = context.reader.type
    if (type == IonType.NULL) {
      property.set(hostObject, null)
    }
    else if (type != IonType.INT) {
      property.set(hostObject, readArray(context, hostObject))
    }
  }

  override fun serialize(obj: Any, context: WriteContext) {
    val array = obj as Array<*>
    val writer = context.writer
    if (context.filter.skipEmptyArray && array.isEmpty()) {
      writer.writeInt(0)
      return
    }

    writer.stepIn(IonType.LIST)
    val consumer = createItemConsumer(context)
    for (item in array) {
      consumer(item)
    }
    writer.stepOut()
  }

  private fun readArray(context: ReadContext, hostObject: Any?): Array<out Any> {
    val list = ArrayList<Any?>()
    readInto(hostObject, list, context)
    val result = ArrayUtil.newArray(itemClass, list.size)
    list.toArray(result)
    return result
  }
}