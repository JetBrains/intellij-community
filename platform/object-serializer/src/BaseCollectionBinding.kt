// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonType
import java.lang.reflect.Type
import java.util.function.Consumer

internal abstract class BaseCollectionBinding(itemType: Type, context: BindingInitializationContext) : Binding {
  private val itemBinding = createElementBindingByType(itemType, context)

  protected fun createItemConsumer(context: WriteContext): Consumer<Any?> {
    val writer = context.writer
    return Consumer {
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
      @Suppress("MoveVariableDeclarationIntoWhen")
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