// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization

import com.amazon.ion.IonType
import java.lang.reflect.Type

internal abstract class BaseCollectionBinding(itemType: Type, context: BindingInitializationContext) : Binding {
  private val itemBinding = createElementBindingByType(itemType, context)

  protected fun createItemConsumer(context: WriteContext): (Any?) -> Unit {
    val writer = context.writer
    return {
      if (it == null) {
        writer.writeNull()
      }
      else {
        itemBinding.serialize(it, context)
      }
    }
  }

  fun readInto(hostObject: Any?, result: MutableCollection<Any?>, context: ReadContext) {
    val reader = context.reader
    reader.stepIn()
    while (true) {
      val type = reader.next() ?: break
      val item = when (type) {
        IonType.NULL -> null
        else -> itemBinding.deserialize(context, hostObject)
      }
      result.add(item)
    }
    reader.stepOut()
  }
}