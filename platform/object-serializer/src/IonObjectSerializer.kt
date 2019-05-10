// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization

import com.intellij.util.ParameterizedTypeImpl
import software.amazon.ion.IonType
import software.amazon.ion.system.IonBinaryWriterBuilder
import software.amazon.ion.system.IonTextWriterBuilder
import software.amazon.ion.system.IonWriterBuilder
import java.io.OutputStream
import java.lang.reflect.Type
import kotlin.experimental.or

internal class IonObjectSerializer {
  // by default only fields (including private)
  private val propertyCollector = PropertyCollector(PropertyCollector.COLLECT_PRIVATE_FIELDS or PropertyCollector.COLLECT_FINAL_FIELDS)

  internal val bindingProducer = IonBindingProducer(propertyCollector)

  fun write(obj: Any, outputStream: OutputStream, filter: SerializationFilter, binary: Boolean = true, originalType: Type? = null) {
    createIonWriterBuilder(binary).build(outputStream).use { ionWriter ->
      val aClass = obj.javaClass
      bindingProducer.getRootBinding(aClass, originalType ?: aClass).serialize(obj, WriteContext(ionWriter, filter, ObjectIdWriter()))
    }
  }

  fun <T> read(objectClass: Class<T>, reader: ValueReader): T {
    reader.use {
      when (reader.next()) {
        IonType.NULL -> throw SerializationException("root value is null")
        null -> throw SerializationException("empty input")
        else -> {
          @Suppress("UNCHECKED_CAST")
          return bindingProducer.getRootBinding(objectClass).deserialize(ReadContext(reader, ObjectIdReader())) as T
        }
      }
    }
  }

  fun <T> readList(itemClass: Class<T>, reader: ValueReader): List<T> {
    reader.use {
      @Suppress("UNCHECKED_CAST")
      when (reader.next()) {
        IonType.NULL -> throw SerializationException("root value is null")
        null -> throw SerializationException("empty input")
        else -> {
          val result = mutableListOf<Any?>()
          val binding = bindingProducer.getRootBinding(ArrayList::class.java, ParameterizedTypeImpl(ArrayList::class.java, itemClass)) as CollectionBinding
          binding.readInto(result as MutableCollection<Any?>, ReadContext(reader, ObjectIdReader()))
          return result as List<T>
        }
      }
    }
  }
}

private fun createIonWriterBuilder(binary: Boolean): IonWriterBuilder {
  if (binary) {
    return IonBinaryWriterBuilder.standard()
  }
  else {
    // line separator is not configurable and platform-dependent (https://github.com/amzn/ion-java/issues/57)
    return IonTextWriterBuilder.pretty()
  }
}