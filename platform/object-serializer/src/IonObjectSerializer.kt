// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization

import com.intellij.util.ParameterizedTypeImpl
import org.objenesis.Objenesis
import org.objenesis.ObjenesisStd
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

  private val objenesis = lazy {
    // ObjectInstantiator is cached by BeanBinding
    ObjenesisStd(/* useCache = */ false)
  }

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
          return bindingProducer.getRootBinding(objectClass).deserialize(createReadContext(reader)) as T
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
          binding.readInto(result as MutableCollection<Any?>, createReadContext(reader))
          return result as List<T>
        }
      }
    }
  }

  private fun createReadContext(reader: ValueReader): ReadContext {
    return ReadContextImpl(reader, ObjectIdReader(), objenesis)
  }
}

private data class ReadContextImpl(override val reader: ValueReader, override val objectIdReader: ObjectIdReader, private val lazyObjenesis: Lazy<Objenesis>) : ReadContext {
  override val objenesis: Objenesis
    get() = lazyObjenesis.value
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