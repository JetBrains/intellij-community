// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization

import com.intellij.util.ArrayUtil
import software.amazon.ion.IonType
import java.util.function.Consumer

internal abstract class BaseCollectionBinding(itemClass: Class<*>, context: BindingInitializationContext) : RootBinding, NestedBinding {
  private val itemBinding = context.bindingProducer.getRootBinding(itemClass)

  final override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    write(hostObject, property, context) {
      serialize(it, context)
    }
  }

  override fun deserialize(context: ReadContext): ArrayList<Any?> {
    val result = ArrayList<Any?>()
    readInto(result, context)
    return result
  }

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

  fun readInto(result: MutableCollection<Any?>, context: ReadContext) {
    val reader = context.reader
    reader.stepIn()
    while (true) {
      @Suppress("MoveVariableDeclarationIntoWhen")
      val type = reader.next() ?: break
      val item = when (type) {
        IonType.NULL -> null
        else -> itemBinding.deserialize(context)
      }
      result.add(item)
    }
    reader.stepOut()
  }
}

internal class CollectionBinding(itemClass: Class<*>, context: BindingInitializationContext) : BaseCollectionBinding(itemClass, context) {
  override fun serialize(obj: Any, context: WriteContext) {
    val writer = context.writer
    val collection = obj as Collection<*>
    writer.stepIn(IonType.LIST)
    collection.forEach(createItemConsumer(context))
    writer.stepOut()
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    val type = context.reader.type
    if (type == IonType.NULL) {
      property.set(hostObject, null)
      return
    }

    @Suppress("UNCHECKED_CAST")
    var result = property.read(hostObject) as MutableCollection<Any?>?
    if (result != null && ClassUtil.isMutableCollection(result)) {
      result.clear()
    }
    else {
      result = ArrayList()
      property.set(hostObject, result)
    }
    readInto(result, context)
  }
}


internal class ArrayBinding(private val itemClass: Class<*>, context: BindingInitializationContext) : BaseCollectionBinding(itemClass, context) {
  override fun serialize(obj: Any, context: WriteContext) {
    val writer = context.writer
    writer.stepIn(IonType.LIST)
    val consumer = createItemConsumer(context)
    (obj as Array<*>).forEach { consumer.accept(it) }
    writer.stepOut()
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    read(hostObject, property, context) {
      val list = deserialize(context)
      val result = ArrayUtil.newArray(itemClass, list.size)
      list.toArray(result)
      result
    }
  }
}