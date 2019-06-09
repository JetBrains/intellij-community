// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

internal class PolymorphicBinding(private val superClass: Class<*>) : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    val valueClass = obj.javaClass
    val writer = context.writer

    if (!context.configuration.allowAnySubTypes) {
      throw SerializationException("Polymorphic type without specified allowed sub types is forbidden")
    }

    writer.addTypeAnnotation(valueClass.name)
    context.bindingProducer.getRootBinding(valueClass).serialize(obj, context)
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    read(hostObject, property, context) {
      doDeserialize(context, hostObject)
    }
  }

  override fun deserialize(context: ReadContext): Any {
    return doDeserialize(context, null)!!
  }

  private fun doDeserialize(context: ReadContext, hostObject: Any?): Any? {
    if (!context.configuration.allowAnySubTypes) {
      throw SerializationException("Polymorphic type without specified allowed sub types is forbidden")
    }

    val reader = context.reader
    val beanClass: Class<*>
    val typeAnnotationIterator = reader.iterateTypeAnnotations()
    if (typeAnnotationIterator.hasNext()) {
      val className = typeAnnotationIterator.next()
      val loadClass = context.configuration.loadClass
      // loadClass for now doesn't support map or collection as host object
      if (loadClass == null || hostObject == null) {
        beanClass = javaClass.classLoader.loadClass(className)
      }
      else {
        beanClass = loadClass(className, hostObject) ?: return null
      }

      if (!superClass.isAssignableFrom(beanClass)) {
        throw SerializationException("Class \"$className\" must be assignable to \"${superClass.name}\"")
      }
    }
    else {
      throw SerializationException("Class name is not specified")
    }
    return context.bindingProducer.getRootBinding(beanClass).deserialize(context)
  }
}