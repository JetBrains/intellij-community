// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

internal class PolymorphicBinding(private val superClass: Class<*>) : RootBinding {
  override fun serialize(obj: Any, context: WriteContext) {
    val valueClass = obj.javaClass
    val writer = context.writer

    if (!context.configuration.allowAnySubTypes) {
      throw SerializationException("Polymorphic type without specified allowed sub types is forbidden")
    }

    writer.addTypeAnnotation(valueClass.name)
    context.bindingProducer.getRootBinding(valueClass).serialize(obj, context)
  }

  override fun deserialize(context: ReadContext): Any {
    if (!context.configuration.allowAnySubTypes) {
      throw SerializationException("Polymorphic type without specified allowed sub types is forbidden")
    }

    val reader = context.reader
    val beanClass: Class<*>
    val typeAnnotationIterator = reader.iterateTypeAnnotations()
    if (typeAnnotationIterator.hasNext()) {
      val className = typeAnnotationIterator.next()
      beanClass = (context.configuration.classLoader ?: javaClass.classLoader).loadClass(className)
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