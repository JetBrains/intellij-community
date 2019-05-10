// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization

import com.intellij.util.ParameterizedTypeImpl
import com.intellij.util.containers.ObjectIntHashMap
import gnu.trove.TIntObjectHashMap
import gnu.trove.TObjectHashingStrategy
import software.amazon.ion.IonReader
import software.amazon.ion.IonType
import software.amazon.ion.IonWriter
import software.amazon.ion.system.IonBinaryWriterBuilder
import software.amazon.ion.system.IonReaderBuilder
import software.amazon.ion.system.IonTextWriterBuilder
import software.amazon.ion.system.IonWriterBuilder
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.lang.reflect.Type
import kotlin.experimental.or

internal typealias ValueReader = IonReader
internal typealias ValueWriter = IonWriter

class ObjectSerializer {
  companion object {
    private val DEFAULT_FILTER = object : SerializationFilter {
      override fun isSkipped(value: Any?) = false
    }
  }

  // by default only fields (including private)
  private val propertyCollector = PropertyCollector(PropertyCollector.COLLECT_PRIVATE_FIELDS or PropertyCollector.COLLECT_FINAL_FIELDS)

  private val bindingProducer = IonBindingProducer(propertyCollector)

  @JvmOverloads
  fun write(obj: Any, outputStream: OutputStream, filter: SerializationFilter = DEFAULT_FILTER, binary: Boolean = true) {
    doWrite(obj, outputStream, filter, binary)
  }

  @JvmOverloads
  fun <T> writeList(obj: List<T>, itemClass: Class<T>, outputStream: OutputStream, filter: SerializationFilter = DEFAULT_FILTER, binary: Boolean = true) {
    doWrite(obj, outputStream, filter, binary, ParameterizedTypeImpl(ArrayList::class.java, itemClass))
  }

  private fun doWrite(obj: Any, outputStream: OutputStream, filter: SerializationFilter = DEFAULT_FILTER, binary: Boolean = true, originalType: Type? = null) {
    createIonWriterBuilder(binary).build(outputStream).use { ionWriter ->
      val aClass = obj.javaClass
      bindingProducer.getRootBinding(aClass, originalType ?: aClass).serialize(obj, WriteContext(ionWriter, filter, ObjectIdWriter()))
    }
  }

  fun <T> read(objectClass: Class<T>, inputStream: InputStream): T {
    return read(objectClass, createIonReaderBuilder().build(inputStream))
  }

  fun <T> read(objectClass: Class<T>, reader: Reader): T {
    return read(objectClass, createIonReaderBuilder().build(reader))
  }

  fun <T> read(objectClass: Class<T>, text: String): T {
    return read(objectClass, createIonReaderBuilder().build(text))
  }

  private fun <T> read(objectClass: Class<T>, reader: ValueReader): T {
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

  fun <T> readList(itemClass: Class<T>, reader: Reader): List<T> {
    return readList(itemClass, createIonReaderBuilder().build(reader))
  }

  private fun <T> readList(itemClass: Class<T>, reader: ValueReader): List<T> {
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

private fun createIonReaderBuilder() = IonReaderBuilder.standard()

// not finished concept because not required for object graph serialization
interface SerializationFilter {
  fun isSkipped(value: Any?): Boolean
}

data class WriteContext(val writer: ValueWriter,
                        val filter: SerializationFilter,
                        val objectIdWriter: ObjectIdWriter?)

data class ReadContext(val reader: ValueReader,
                       val objectIdReader: ObjectIdReader)

class ObjectIdWriter {
  private val map: ObjectIntHashMap<Any> = ObjectIntHashMap(TObjectHashingStrategy.IDENTITY)
  private var counter = 0

  fun getId(obj: Any) = map.get(obj)

  fun registerObject(obj: Any): Int {
    val id = counter++
    val previous = map.put(obj, id)
    // TObjectIntHashMap returns previous as 0 even for a new key
    assert(previous <= 0)
    return id
  }
}

class ObjectIdReader {
  private val map: TIntObjectHashMap<Any> = TIntObjectHashMap()

  fun getObject(id: Int): Any {
    return map.get(id) ?: throw SerializationException("Cannot find object by id $id")
  }

  fun registerObject(obj: Any, id: Int) {
    val previous = map.put(id, obj)
    assert(previous == null)
  }
}