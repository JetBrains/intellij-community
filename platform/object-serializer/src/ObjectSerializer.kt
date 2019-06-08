// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonReader
import com.amazon.ion.IonWriter
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.ParameterizedTypeImpl
import com.intellij.util.containers.ObjectIntHashMap
import gnu.trove.TIntObjectHashMap
import gnu.trove.TObjectHashingStrategy
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader

internal typealias ValueReader = IonReader
internal typealias ValueWriter = IonWriter

// not fully initialized object may be passed (only created instance without properties) if object has PropertyMapping annotation
typealias BeanConstructed = (instance: Any) -> Any

internal val defaultWriteConfiguration = WriteConfiguration()
internal val LOG = logger<ObjectSerializer>()

val defaultReadConfiguration = ReadConfiguration()

/**
 * @see [VersionedFile]
 */
class ObjectSerializer {
  companion object {
    @JvmStatic
    val instance = ObjectSerializer()
  }

  private val readerBuilder
    get() = serializer.readerBuilder

  internal val serializer = IonObjectSerializer()

  @JvmOverloads
  fun writeAsBytes(obj: Any, configuration: WriteConfiguration = defaultWriteConfiguration): ByteArray {
    val out = BufferExposingByteArrayOutputStream()
    serializer.write(obj, out, configuration)
    return out.toByteArray()
  }

  @JvmOverloads
  fun write(obj: Any, outputStream: OutputStream, configuration: WriteConfiguration = defaultWriteConfiguration) {
    serializer.write(obj, outputStream, configuration)
  }

  @JvmOverloads
  fun <T> writeList(obj: Collection<T>, itemClass: Class<T>, outputStream: OutputStream, configuration: WriteConfiguration = defaultWriteConfiguration) {
    serializer.write(obj, outputStream, configuration, ParameterizedTypeImpl(Collection::class.java, itemClass))
  }

  fun <T> read(objectClass: Class<T>, bytes: ByteArray, configuration: ReadConfiguration = defaultReadConfiguration): T {
    return serializer.read(objectClass, readerBuilder.build(bytes), configuration)
  }

  fun <T> read(objectClass: Class<T>, inputStream: InputStream, configuration: ReadConfiguration = defaultReadConfiguration): T {
    return serializer.read(objectClass, readerBuilder.build(inputStream), configuration)
  }

  fun <T> read(objectClass: Class<T>, reader: Reader, configuration: ReadConfiguration = defaultReadConfiguration): T {
    return serializer.read(objectClass, readerBuilder.build(reader), configuration)
  }

  fun <T> read(objectClass: Class<T>, text: String, configuration: ReadConfiguration = defaultReadConfiguration): T {
    return serializer.read(objectClass, readerBuilder.build(text), configuration)
  }

  fun <T> readList(itemClass: Class<T>, reader: Reader, configuration: ReadConfiguration = defaultReadConfiguration): List<T> {
    return serializer.readList(itemClass, readerBuilder.build(reader), configuration)
  }

  @JvmOverloads
  fun <T> readList(itemClass: Class<T>, bytes: ByteArray, configuration: ReadConfiguration = defaultReadConfiguration): List<T> {
    return serializer.readList(itemClass, readerBuilder.build(bytes), configuration)
  }

  @JvmOverloads
  fun <T> readList(itemClass: Class<T>, input: InputStream, configuration: ReadConfiguration = defaultReadConfiguration): List<T> {
    return serializer.readList(itemClass, readerBuilder.build(input), configuration)
  }
}

interface SerializationFilter {
  val skipEmptyCollection: Boolean
    get() = false

  val skipEmptyMap: Boolean
    get() = false

  val skipEmptyArray: Boolean
    get() = false

  fun isSkipped(value: Any?): Boolean
}

object SkipNullAndEmptySerializationFilter : SerializationFilter {
  override fun isSkipped(value: Any?) = value == null

  override val skipEmptyCollection: Boolean
    get() = true

  override val skipEmptyMap: Boolean
    get() = true

  override val skipEmptyArray: Boolean
    get() = true
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
    return map.get(id)
           ?: throw SerializationException("Cannot find object by id $id")
  }

  fun registerObject(obj: Any, id: Int) {
    val previous = map.put(id, obj)
    assert(previous == null)
  }
}