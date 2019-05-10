// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization

import com.intellij.util.containers.ObjectIntHashMap
import software.amazon.ion.IonType
import java.lang.reflect.Type

internal class BeanBinding(beanClass: Class<*>) : BaseBeanBinding(beanClass), RootBinding {
  private lateinit var bindings: Array<NestedBinding>
  private lateinit var nameToBindingIndex: ObjectIntHashMap<String>
  private lateinit var accessors: List<MutableAccessor>

  override fun init(originalType: Type, context: BindingInitializationContext) {
    val list = context.propertyCollector.collect(beanClass)
    accessors = list
    val nameToBindingIndex = ObjectIntHashMap<String>(list.size)
    bindings = Array(list.size) { index ->
      val accessor = list.get(index)
      val binding = context.bindingProducer.getNestedBinding(accessor)
      nameToBindingIndex.put(accessor.name, index)
      binding
    }
    this.nameToBindingIndex = nameToBindingIndex
  }

  override fun serialize(obj: Any, context: WriteContext) {
    val writer = context.writer

    val objectIdWriter = context.objectIdWriter
    if (objectIdWriter != null) {
      val alreadySerializedReference = objectIdWriter.getId(obj)
      if (alreadySerializedReference != -1) {
        writer.writeInt(alreadySerializedReference.toLong())
        return
      }
    }

    writer.stepIn(IonType.STRUCT)

    if (objectIdWriter != null) {
      // id as field because annotation supports only string, but it is not efficient
      writer.setFieldName("@id")
      writer.writeInt(objectIdWriter.registerObject(obj).toLong())
    }

    val bindings = bindings
    val accessors = accessors
    for (i in 0 until bindings.size) {
      bindings[i].serialize(obj, accessors[i], context)
    }
    writer.stepOut()
  }

  override fun deserialize(context: ReadContext): Any {
    val reader = context.reader

    if (reader.type == IonType.INT) {
      // reference
      return context.objectIdReader.getObject(reader.intValue())
    }

    reader.stepIn()

    val obj = newInstance()
    val nameToBindingIndex = nameToBindingIndex
    val bindings = bindings
    val accessors = accessors

    while (true) {
      reader.next() ?: break
      val fieldName = reader.fieldName

      if (fieldName == "@id") {
        val id = reader.intValue()
        context.objectIdReader.registerObject(obj, id)
        continue
      }

      val bindingIndex = nameToBindingIndex.get(fieldName)
      // ignore unknown field
      if (bindingIndex == -1) {
        // log.debug?
        continue
      }

      bindings[bindingIndex].deserialize(obj, accessors[bindingIndex], context)
    }

    reader.stepOut()
    return obj
  }
}