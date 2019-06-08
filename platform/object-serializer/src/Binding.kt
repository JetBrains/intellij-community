// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonType
import java.lang.reflect.Type

internal interface Binding {
  fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    write(hostObject, property, context) {
      serialize(it, context)
    }
  }

  fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    read(hostObject, property, context) {
      deserialize(context)
    }
  }

  fun createCacheKey(aClass: Class<*>?, type: Type) = type

  fun init(originalType: Type, context: BindingInitializationContext) {
  }

  fun serialize(obj: Any, context: WriteContext)

  fun deserialize(context: ReadContext): Any
}

internal interface BindingInitializationContext {
  val propertyCollector: PropertyCollector
  val bindingProducer: BindingProducer

  val isResolveConstructorOnInit: Boolean
    get() = false
}

internal inline fun write(hostObject: Any, accessor: MutableAccessor, context: WriteContext, write: ValueWriter.(value: Any) -> Unit) {
  val value = accessor.readUnsafe(hostObject)
  if (context.filter.isSkipped(value)) {
    return
  }

  val writer = context.writer
  writer.setFieldName(accessor.name)
  if (value == null) {
    writer.writeNull()
  }
  else {
    writer.write(value)
  }
}

internal inline fun read(hostObject: Any, property: MutableAccessor, context: ReadContext, read: ValueReader.() -> Any?) {
  if (context.reader.type == IonType.NULL) {
    property.set(hostObject, null)
  }
  else {
    property.set(hostObject, context.reader.read())
  }
}