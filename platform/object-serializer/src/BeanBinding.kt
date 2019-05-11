// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization

import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.system.IonBinaryWriterBuilder
import com.amazon.ion.system.IonReaderBuilder
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.ObjectIntHashMap
import java.lang.reflect.Constructor
import java.lang.reflect.Type

private val LOG = logger<BeanBinding>()

private val structWriterBuilder by lazy {
  IonBinaryWriterBuilder.standard().withStreamCopyOptimized(true).immutable()
}

private val structReaderBuilder by lazy {
  IonReaderBuilder.standard().immutable()
}

private const val ID_FIELD_NAME = "@id"

internal class BeanBinding(beanClass: Class<*>) : BaseBeanBinding(beanClass), RootBinding {
  private lateinit var bindings: Array<NestedBinding>
  private lateinit var nameToBindingIndex: ObjectIntHashMap<String>
  private lateinit var accessors: List<MutableAccessor>

  private val propertyMapping: Lazy<NonDefaultConstructorInfo?> = lazy {
    for (constructor in beanClass.declaredConstructors) {
      val annotation = constructor.getAnnotation(PropertyMapping::class.java) ?: continue
      try {
        constructor.isAccessible = true
      }
      catch (ignore: SecurityException) {
      }

      if (constructor.parameterCount != annotation.value.size) {
        throw SerializationException("PropertyMapping annotation specifies ${annotation.value.size} parameters, " +
                                     "but constructor accepts ${constructor.parameterCount}")
      }
      return@lazy NonDefaultConstructorInfo(annotation.value, constructor)
    }

    null
  }

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

    if (context.isResolveConstructorOnInit) {
      resolveConstructor()
    }
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
      writer.setFieldName(ID_FIELD_NAME)
      writer.writeInt(objectIdWriter.registerObject(obj).toLong())
    }

    val bindings = bindings
    val accessors = accessors
    for (i in 0 until bindings.size) {
      bindings[i].serialize(obj, accessors[i], context)
    }
    writer.stepOut()
  }

  private fun createUsingCustomConstructor(context: ReadContext): Any {
    val constructorInfo = propertyMapping.value ?: throw SerializationException("Please annotate non-default constructor with PropertyMapping")

    val names = constructorInfo.names
    val initArgs = arrayOfNulls<Any?>(names.size)

    val out = context.allocateByteArrayOutputStream()
    // ionType is already checked - so, struct is expected
    structWriterBuilder.build(out).use { it.writeValue(context.reader) }

    // we cannot read all field values before creating instance because some field value can reference to parent - our instance,
    // so, first, create instance, and only then read rest of fields
    structReaderBuilder.build(out.internalBuffer, 0, out.size()).use { reader ->
      reader.next()
      val subReadContext = context.createSubContext(reader)
      readStruct(reader) { fieldName, _ ->
        val argIndex = names.indexOf(fieldName)
        if (argIndex == -1) {
          return@readStruct
        }

        val bindingIndex = nameToBindingIndex.get(fieldName)
        if (bindingIndex == -1) {
          LOG.error("Cannot find binding for field $fieldName")
          return@readStruct
        }

        initArgs[argIndex] = (bindings[bindingIndex]).deserialize(subReadContext)
      }
    }

    val instance = constructorInfo.constructor.newInstance(*initArgs)
    if (bindings.size > names.size) {
      structReaderBuilder.build(out.internalBuffer, 0, out.size()).use { reader ->
        reader.next()
        readIntoObject(instance, context.createSubContext(reader)) { !names.contains(it) }
      }
    }
    return instance
  }

  override fun deserialize(context: ReadContext): Any {
    val reader = context.reader

    val ionType = reader.type
    if (ionType == IonType.INT) {
      // reference
      return context.objectIdReader.getObject(reader.intValue())
    }
    else if (ionType != IonType.STRUCT) {
      throw SerializationException("Expected STRUCT, but got $ionType")
    }

    if (propertyMapping.isInitialized()) {
      return createUsingCustomConstructor(context)
    }

    val instance = try {
      resolveConstructor().newInstance()
    }
    catch (e: SecurityException) {
      beanClass.newInstance()
    }
    catch (e: NoSuchMethodException) {
      return createUsingCustomConstructor(context)
    }

    readIntoObject(instance, context)

    return context.beanConstructed?.let { it(instance) } ?: instance
  }

  private fun readIntoObject(instance: Any, context: ReadContext, filter: ((fieldName: String) -> Boolean)? = null) {
    val nameToBindingIndex = nameToBindingIndex
    val bindings = bindings
    val accessors = accessors
    val reader = context.reader
    readStruct(reader) { fieldName, type ->
      if (type == IonType.INT && fieldName == ID_FIELD_NAME) {
        val id = reader.intValue()
        context.objectIdReader.registerObject(instance, id)
        return@readStruct
      }

      if (filter != null && !filter(fieldName)) {
        return@readStruct
      }

      val bindingIndex = nameToBindingIndex.get(fieldName)
      // ignore unknown field
      if (bindingIndex == -1) {
        LOG.debug("Unknown field $fieldName for ${beanClass}")
        return@readStruct
      }

      bindings[bindingIndex].deserialize(instance, accessors[bindingIndex], context)
    }
  }
}

private inline fun readStruct(reader: IonReader, read: (fieldName: String, type: IonType) -> Unit) {
  reader.stepIn()
  while (true) {
    val type = reader.next() ?: break
    read(reader.fieldName, type)
  }
  reader.stepOut()
}

private class NonDefaultConstructorInfo(val names: Array<String>, val constructor: Constructor<*>)