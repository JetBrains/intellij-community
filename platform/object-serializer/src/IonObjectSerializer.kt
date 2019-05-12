// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonType
import com.amazon.ion.system.IonBinaryWriterBuilder
import com.amazon.ion.system.IonReaderBuilder
import com.amazon.ion.system.IonTextWriterBuilder
import com.amazon.ion.system.IonWriterBuilder
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.ParameterizedTypeImpl
import java.io.OutputStream
import java.lang.reflect.Type
import kotlin.experimental.or

internal class IonObjectSerializer {
  val readerBuilder: IonReaderBuilder = IonReaderBuilder.standard().immutable()

  // by default only fields (including private)
  private val propertyCollector = PropertyCollector(PropertyCollector.COLLECT_PRIVATE_FIELDS or PropertyCollector.COLLECT_FINAL_FIELDS)

  internal val bindingProducer = IonBindingProducer(propertyCollector)

  fun write(obj: Any, outputStream: OutputStream, configuration: WriteConfiguration?, originalType: Type? = null) {
    createIonWriterBuilder(configuration?.binary ?: true).build(outputStream).use { ionWriter ->
      val aClass = obj.javaClass
      bindingProducer.getRootBinding(aClass, originalType ?: aClass).serialize(obj, WriteContext(ionWriter, configuration?.filter ?: DEFAULT_FILTER, ObjectIdWriter()))
    }
  }

  fun <T> read(objectClass: Class<T>, reader: ValueReader, beanConstructed: BeanConstructed? = null): T {
    reader.use {
      when (reader.next()) {
        IonType.NULL -> throw SerializationException("root value is null")
        null -> throw SerializationException("empty input")
        else -> {
          @Suppress("UNCHECKED_CAST")
          return bindingProducer.getRootBinding(objectClass).deserialize(createReadContext(reader, beanConstructed)) as T
        }
      }
    }
  }

  fun <T> readList(itemClass: Class<T>, reader: ValueReader, beanConstructed: BeanConstructed?): List<T> {
    reader.use {
      @Suppress("UNCHECKED_CAST")
      when (reader.next()) {
        IonType.NULL -> throw SerializationException("root value is null")
        null -> throw SerializationException("empty input")
        else -> {
          val result = mutableListOf<Any?>()
          val binding = bindingProducer.getRootBinding(ArrayList::class.java, ParameterizedTypeImpl(ArrayList::class.java, itemClass)) as CollectionBinding
          binding.readInto(result as MutableCollection<Any?>, createReadContext(reader, beanConstructed))
          return result as List<T>
        }
      }
    }
  }

  private fun createReadContext(reader: ValueReader, beanConstructed: BeanConstructed? = null): ReadContext {
    return ReadContextImpl(reader, ObjectIdReader(), beanConstructed)
  }
}

private val DEFAULT_FILTER = object : SerializationFilter {
  override fun isSkipped(value: Any?) = false
}

private data class ReadContextImpl(override val reader: ValueReader,
                                   override val objectIdReader: ObjectIdReader,
                                   override val beanConstructed: BeanConstructed?) : ReadContext {
  private var byteArrayOutputStream: BufferExposingByteArrayOutputStream? = null

  override fun allocateByteArrayOutputStream(): BufferExposingByteArrayOutputStream {
    var result = byteArrayOutputStream
    if (result == null) {
      result = BufferExposingByteArrayOutputStream(8 * 1024)
      byteArrayOutputStream = result
    }
    else {
      result.reset()
    }
    return result
  }

  override fun createSubContext(reader: ValueReader) = ReadContextImpl(reader, objectIdReader, beanConstructed)
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