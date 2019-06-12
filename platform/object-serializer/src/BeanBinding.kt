// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.system.IonReaderBuilder
import com.intellij.util.containers.ObjectIntHashMap
import java.lang.reflect.Constructor
import java.lang.reflect.Type

private val structReaderBuilder by lazy {
  IonReaderBuilder.standard().immutable()
}

private const val ID_FIELD_NAME = "@id"

internal class BeanBinding(beanClass: Class<*>) : BaseBeanBinding(beanClass), Binding {
  private lateinit var bindings: Array<Binding>
  private lateinit var nameToBindingIndex: ObjectIntHashMap<String>
  private lateinit var properties: List<MutableAccessor>

  private val propertyMapping: Lazy<NonDefaultConstructorInfo?> = lazy {
    computeNonDefaultConstructorInfo(beanClass)
  }

  // type parameters for bean binding doesn't play any role, should be the only binding for such class
  override fun createCacheKey(aClass: Class<*>?, type: Type) = aClass!!

  override fun init(originalType: Type, context: BindingInitializationContext) {
    val list = context.propertyCollector.collect(beanClass)
    properties = list
    val nameToBindingIndex = ObjectIntHashMap<String>(list.size)
    bindings = Array(list.size) { index ->
      val accessor = list.get(index)
      val binding = context.bindingProducer.getNestedBinding(accessor)
      nameToBindingIndex.put(accessor.name, index)
      binding
    }
    this.nameToBindingIndex = nameToBindingIndex

    if (context.isResolveConstructorOnInit) {
      try {
        resolveConstructor()
      }
      catch (e: NoSuchMethodException) {
        propertyMapping.value
      }
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
    val properties = properties
    for (i in 0 until bindings.size) {
      val property = properties[i]
      val binding = bindings[i]
      try {
        binding.serialize(obj, property, context)
      }
      catch (e: Exception) {
        throw SerializationException("Cannot serialize property (property=$property, binding=$binding, beanClass=${beanClass.name})", e)
      }
    }
    writer.stepOut()
  }

  private fun createUsingCustomConstructor(context: ReadContext): Any {
    val constructorInfo = propertyMapping.value
                          ?: throw SerializationException("Please annotate non-default constructor with PropertyMapping (beanClass=${beanClass.name})")
    val names = constructorInfo.names
    val initArgs = arrayOfNulls<Any?>(names.size)

    val out = context.allocateByteArrayOutputStream()
    // ionType is already checked - so, struct is expected
    binaryWriterBuilder.newWriter(out).use { it.writeValue(context.reader) }

    // we cannot read all field values before creating instance because some field value can reference to parent - our instance,
    // so, first, create instance, and only then read rest of fields
    var id = -1
    structReaderBuilder.build(out.internalBuffer, 0, out.size()).use { reader ->
      reader.next()
      val subReadContext = context.createSubContext(reader)
      readStruct(reader) { fieldName, type ->
        if (type == IonType.NULL) {
          return@readStruct
        }

        if (type == IonType.INT && fieldName == ID_FIELD_NAME) {
          id = reader.intValue()
          return@readStruct
        }

        val argIndex = names.indexOf(fieldName)
        if (argIndex == -1) {
          return@readStruct
        }

        val bindingIndex = nameToBindingIndex.get(fieldName)
        if (bindingIndex == -1) {
          LOG.error("Cannot find binding (fieldName=$fieldName, valueType=${reader.type}, beanClass=${beanClass.name}")
          return@readStruct
        }

        val binding = bindings[bindingIndex]
        try {
          initArgs[argIndex] = binding.deserialize(subReadContext)
        }
        catch (e: Exception) {
          throw SerializationException("Cannot deserialize parameter value (fieldName=$fieldName, binding=$binding, valueType=${reader.type}, beanClass=${beanClass.name})", e)
        }
      }
    }

    var instance = try {
      constructorInfo.constructor.newInstance(*initArgs)
    }
    catch (e: Exception) {
      throw SerializationException("Cannot create instance (beanClass=${beanClass.name}, initArgs=${initArgs.joinToString()})", e)
    }

    // must be called after creation because child properties can reference object
    context.configuration.beanConstructed?.let {
      instance = it(instance)
    }

    if (id != -1) {
      context.objectIdReader.registerObject(instance, id)
    }

    if (bindings.size > names.size) {
      structReaderBuilder.build(out.internalBuffer, 0, out.size()).use { reader ->
        reader.next()
        readIntoObject(instance, context.createSubContext(reader), checkId = false /* already registered */) { !names.contains(it) }
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
      var stringValue = ""
      if (ionType == IonType.SYMBOL || ionType == IonType.STRING) {
        stringValue = reader.stringValue()
      }
      throw SerializationException("Expected STRUCT, but got $ionType (stringValue=$stringValue)")
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

    return context.configuration.beanConstructed?.let { it(instance) } ?: instance
  }

  private fun readIntoObject(instance: Any, context: ReadContext, checkId: Boolean = true, filter: ((fieldName: String) -> Boolean)? = null) {
    val nameToBindingIndex = nameToBindingIndex
    val bindings = bindings
    val accessors = properties
    val reader = context.reader
    readStruct(reader) { fieldName, type ->
      if (type == IonType.INT && fieldName == ID_FIELD_NAME) {
        // check flag checkId only here, to ensure that @id is not reported as unknown field
        if (checkId) {
          val id = reader.intValue()
          context.objectIdReader.registerObject(instance, id)
        }
        return@readStruct
      }

      if (filter != null && !filter(fieldName)) {
        return@readStruct
      }

      val bindingIndex = nameToBindingIndex.get(fieldName)
      // ignore unknown field
      if (bindingIndex == -1) {
        context.errors.unknownFields.add(ReadError("Unknown field (fieldName=$fieldName, beanClass=${beanClass.name})"))
        return@readStruct
      }

      val binding = bindings[bindingIndex]
      try {
        binding.deserialize(instance, accessors[bindingIndex], context)
      }
      catch (e: SerializationException) {
        throw e
      }
      catch (e: Exception) {
        throw SerializationException("Cannot deserialize field value (field=$fieldName, binding=$binding, valueType=${reader.type}, beanClass=${beanClass.name})", e)
      }
    }
  }
}

private inline fun readStruct(reader: IonReader, read: (fieldName: String, type: IonType) -> Unit) {
  reader.stepIn()
  while (true) {
    val type = reader.next() ?: break
    val fieldName = reader.fieldName
    if (fieldName == null) {
      throw IllegalStateException("No valid current value or the current value is not a field of a struct.")
    }
    read(fieldName, type)
  }
  reader.stepOut()
}

private class NonDefaultConstructorInfo(val names: Array<String>, val constructor: Constructor<*>)

private fun computeNonDefaultConstructorInfo(beanClass: Class<*>): NonDefaultConstructorInfo? {
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
    return NonDefaultConstructorInfo(annotation.value, constructor)
  }

  return null
}