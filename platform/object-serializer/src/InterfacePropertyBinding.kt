// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

@Suppress("ArrayInDataClass")
internal data class InterfacePropertyBinding(private val allowedTypes: Array<Class<*>>) : Binding {
  override fun serialize(obj: Any, context: WriteContext) = throw IllegalStateException("InterfacePropertyBinding cannot be used as root binding")

  override fun deserialize(context: ReadContext, hostObject: Any?) = throw IllegalStateException("InterfacePropertyBinding cannot be used as root binding")

  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    write(hostObject, property, context) { value ->
      val valueClass = value.javaClass
      if (!allowedTypes.contains(valueClass)) {
        throw SerializationException("Type $valueClass is not allowed for field ${property.name}")
      }

      addTypeAnnotation(valueClass.simpleName)
      context.bindingProducer.getRootBinding(valueClass).serialize(value, context)
    }
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    read(hostObject, property, context) {
      val beanClass: Class<*>
      val typeAnnotationIterator = iterateTypeAnnotations()
      if (typeAnnotationIterator.hasNext()) {
        val simpleName = typeAnnotationIterator.next()
        beanClass = allowedTypes.firstOrNull { it.simpleName == simpleName } ?: throw SerializationException(
          "Unknown class simple name: $simpleName (allowedClasses=$allowedTypes)")
      }
      else {
        throw SerializationException("Class simple name is not specified (allowedClasses=$allowedTypes)")
      }
      context.bindingProducer.getRootBinding(beanClass).deserialize(context, hostObject)
    }
  }
}