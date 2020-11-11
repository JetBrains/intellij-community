// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.system.IonReaderBuilder
import it.unimi.dsi.fastutil.objects.Object2IntMap
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.lang.reflect.Type
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaConstructor

private val structReaderBuilder by lazy {
  IonReaderBuilder.standard().immutable()
}

private const val ID_FIELD_NAME = "@id"

internal class BeanBinding(beanClass: Class<*>) : BaseBeanBinding(beanClass), Binding {
  private lateinit var bindings: Array<Binding>
  private lateinit var nameToBindingIndex: Object2IntMap<String>
  private lateinit var properties: List<MutableAccessor>

  private val propertyMapping: Lazy<NonDefaultConstructorInfo?> = lazy {
    computeNonDefaultConstructorInfo(beanClass)
  }

  // type parameters for bean binding doesn't play any role, should be the only binding for such class
  override fun createCacheKey(aClass: Class<*>?, type: Type) = aClass!!

  override fun init(originalType: Type, context: BindingInitializationContext) {
    val list = context.propertyCollector.collect(beanClass)
    properties = list
    val nameToBindingIndex = Object2IntOpenHashMap<String>(list.size)
    nameToBindingIndex.defaultReturnValue(-1)
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
    @Suppress("ReplaceManualRangeWithIndicesCalls")
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

  private fun createUsingCustomConstructor(context: ReadContext, hostObject: Any?): Any {
    var constructorInfo = propertyMapping.value
    if (constructorInfo == null) {
      constructorInfo = context.configuration.resolvePropertyMapping?.invoke(beanClass)
                        ?: getPropertyMappingIfDataClass()
                        ?: throw SerializationException("Please annotate non-default constructor with PropertyMapping (beanClass=${beanClass.name})")
    }

    val names = constructorInfo.names
    val initArgs = arrayOfNulls<Any?>(names.size)


    /**
     * Applies [body] to `context.reader` and makes a copy of the structure being read if the second pass will be required
     * to handle properties which are not deserialized by invoking the constructor.
     */
    fun doReadAndMakeCopyIfNeedsSecondPass(body: (reader: IonReader) -> Unit): BufferExposingByteArrayOutputStream? {
      return if (bindings.size > names.size) {
        val out = context.allocateByteArrayOutputStream()
        // ionType is already checked - so, struct is expected
        binaryWriterBuilder.newWriter(out).use { it.writeValue(context.reader) }
        structReaderBuilder.build(out.internalBuffer, 0, out.size()).use { reader ->
          reader.next()
          body(reader)
        }
        out
      }
      else {
        body(context.reader)
        null
      }
    }

    // we cannot read all field values before creating instance because some field value can reference to parent - our instance,
    // so, first, create instance, and only then read rest of fields
    var id = -1
    val out = doReadAndMakeCopyIfNeedsSecondPass { reader ->

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

        val bindingIndex = nameToBindingIndex.getInt(fieldName)
        if (bindingIndex == -1) {
          LOG.error("Cannot find binding (fieldName=$fieldName, valueType=${reader.type}, beanClass=${beanClass.name}")
          return@readStruct
        }

        val binding = bindings.get(bindingIndex)
        try {
          initArgs[argIndex] = binding.deserialize(subReadContext, hostObject)
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

    if (out != null) {
      structReaderBuilder.build(out.internalBuffer, 0, out.size()).use { reader ->
        reader.next()
        readIntoObject(instance, context.createSubContext(reader), checkId = false /* already registered */) { !names.contains(it) }
      }
    }
    return instance
  }

  private fun getPropertyMappingIfDataClass(): NonDefaultConstructorInfo? {
    try {
      // primaryConstructor will be null for Java
      // parameter names are available only via kotlin reflection, not via java reflection
      val kFunction = beanClass.kotlin.primaryConstructor ?: return null
      try {
        kFunction.isAccessible = true
      }
      catch (ignored: SecurityException) {
      }
      val names = kFunction.parameters.mapNotNull { it.name }
      if (names.isEmpty()) {
        return null
      }
      return NonDefaultConstructorInfo(names, kFunction.javaConstructor!!)
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    return null
  }

  override fun deserialize(context: ReadContext, hostObject: Any?): Any {
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
      return createUsingCustomConstructor(context, hostObject)
    }

    val instance = try {
      resolveConstructor().newInstance()
    }
    catch (e: SecurityException) {
      beanClass.newInstance()
    }
    catch (e: NoSuchMethodException) {
      return createUsingCustomConstructor(context, hostObject)
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

      val bindingIndex = nameToBindingIndex.getInt(fieldName)
      // ignore unknown field
      if (bindingIndex == -1) {
        context.errors.unknownFields.add(ReadError("Unknown field (fieldName=$fieldName, beanClass=${beanClass.name})"))
        return@readStruct
      }

      val binding = bindings.get(bindingIndex)
      try {
        binding.deserialize(instance, accessors.get(bindingIndex), context)
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
                                   "but constructor of ${beanClass.name} accepts ${constructor.parameterCount}")
    }
    return NonDefaultConstructorInfo(annotation.value.toList(), constructor)
  }

  return null
}
