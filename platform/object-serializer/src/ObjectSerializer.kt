// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.ParameterizedTypeImpl
import com.intellij.util.containers.ObjectIntHashMap
import gnu.trove.TIntObjectHashMap
import gnu.trove.TObjectHashingStrategy
import org.objenesis.Objenesis
import software.amazon.ion.IonReader
import software.amazon.ion.IonWriter
import software.amazon.ion.system.IonReaderBuilder
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader

internal typealias ValueReader = IonReader
internal typealias ValueWriter = IonWriter
typealias BeanConstructed = (instance: Any) -> Any

class ObjectSerializer {
  companion object {
    @JvmStatic
    val instance = ObjectSerializer()
  }

  internal val serializer = IonObjectSerializer()

  @JvmOverloads
  fun writeAsBytes(obj: Any, configuration: WriteConfiguration? = null): ByteArray {
    val out = BufferExposingByteArrayOutputStream()
    serializer.write(obj, out, configuration)
    return out.toByteArray()
  }

  @JvmOverloads
  fun write(obj: Any, outputStream: OutputStream, configuration: WriteConfiguration? = null) {
    serializer.write(obj, outputStream, configuration)
  }

  @JvmOverloads
  fun <T> writeList(obj: Collection<T>, itemClass: Class<T>, outputStream: OutputStream, configuration: WriteConfiguration? = null) {
    serializer.write(obj, outputStream, configuration, ParameterizedTypeImpl(Collection::class.java, itemClass))
  }

  fun <T> read(objectClass: Class<T>, bytes: ByteArray, beanConstructed: BeanConstructed? = null): T {
    return serializer.read(objectClass, createIonReaderBuilder().build(bytes), beanConstructed)
  }

  fun <T> read(objectClass: Class<T>, inputStream: InputStream): T {
    return serializer.read(objectClass, createIonReaderBuilder().build(inputStream))
  }

  fun <T> read(objectClass: Class<T>, reader: Reader): T {
    return serializer.read(objectClass, createIonReaderBuilder().build(reader))
  }

  fun <T> read(objectClass: Class<T>, text: String): T {
    return serializer.read(objectClass, createIonReaderBuilder().build(text))
  }

  fun <T> readList(itemClass: Class<T>, reader: Reader, beanConstructed: BeanConstructed? = null): List<T> {
    return serializer.readList(itemClass, createIonReaderBuilder().build(reader), beanConstructed)
  }

  @JvmOverloads
  fun <T> readList(itemClass: Class<T>, bytes: ByteArray, beanConstructed: BeanConstructed? = null): List<T> {
    return serializer.readList(itemClass, createIonReaderBuilder().build(bytes), beanConstructed)
  }

  @JvmOverloads
  fun <T> readList(itemClass: Class<T>, input: InputStream, beanConstructed: BeanConstructed? = null): List<T> {
    return serializer.readList(itemClass, createIonReaderBuilder().build(input), beanConstructed)
  }
}

data class WriteConfiguration(val binary: Boolean = true, val filter: SerializationFilter? = null)

private fun createIonReaderBuilder() = IonReaderBuilder.standard()

// not finished concept because not required for object graph serialization
interface SerializationFilter {
  fun isSkipped(value: Any?): Boolean
}

data class WriteContext(val writer: ValueWriter,
                        val filter: SerializationFilter,
                        val objectIdWriter: ObjectIdWriter?)

interface ReadContext {
  val reader: ValueReader
  val objectIdReader: ObjectIdReader
  val objenesis: Objenesis

  val beanConstructed: BeanConstructed?
}

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