// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonException
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter
import com.amazon.ion.system.IonBinaryWriterBuilder
import com.amazon.ion.system.IonReaderBuilder
import com.amazon.ion.system.IonTextWriterBuilder
import com.amazon.ion.system.IonWriterBuilder
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.ParameterizedTypeImpl
import com.intellij.util.io.inputStream
import com.intellij.util.io.outputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.Type
import java.nio.file.Path
import kotlin.experimental.or

private const val FORMAT_VERSION = 1

internal class IonObjectSerializer {
  val readerBuilder: IonReaderBuilder = IonReaderBuilder.standard().immutable()

  // by default only fields (including private)
  private val propertyCollector = PropertyCollector(PropertyCollector.COLLECT_PRIVATE_FIELDS or PropertyCollector.COLLECT_FINAL_FIELDS)

  internal val bindingProducer = IonBindingProducer(propertyCollector)

  @Throws(IOException::class)
  fun writeVersioned(obj: Any, file: Path, fileVersion: Int, configuration: WriteConfiguration = defaultWriteConfiguration, originalType: Type? = null) {
    createIonWriterBuilder(configuration.binary).build(file.outputStream().buffered()).use { writer ->
      writer.stepIn(IonType.STRUCT)

      writer.setFieldName("version")
      writer.writeInt(fileVersion.toLong())

      writer.setFieldName("formatVersion")
      writer.writeInt(FORMAT_VERSION.toLong())

      writer.setFieldName("data")
      doWrite(obj, writer, configuration, originalType)

      writer.stepOut()
    }
  }

  @Throws(IOException::class)
  fun <T : Any> readVersioned(objectClass: Class<T>, file: Path, expectedVersion: Int, configuration: ReadConfiguration, originalType: Type? = null): T? {
    readerBuilder.build(file.inputStream().buffered()).use { reader ->
      @Suppress("UNUSED_VARIABLE")
      var isVersionChecked = 0

      fun logVersionMismatch(prefix: String, currentVersion: Int) {
        LOG.debug { "$prefix version mismatch (file=$file, currentVersion: $currentVersion, expectedVersion=$expectedVersion, objectClass=$objectClass)" }
      }

      try {
        reader.next()
      }
      catch (e: IonException) {
        // corrupted file
        LOG.debug(e)
        return null
      }

      reader.stepIn()
      while (reader.next() != null) {
        when (val fieldName = reader.fieldName) {
          "version" -> {
            val currentVersion = reader.intValue()
            if (currentVersion != expectedVersion) {
              logVersionMismatch("App", currentVersion)
              return null
            }
            @Suppress("UNUSED_CHANGED_VALUE")
            isVersionChecked++
          }
          "formatVersion" -> {
            val currentVersion = reader.intValue()
            if (currentVersion != FORMAT_VERSION) {
              logVersionMismatch("Format", currentVersion)
              return null
            }
            @Suppress("UNUSED_CHANGED_VALUE")
            isVersionChecked++
          }
          "data" -> {
            if (isVersionChecked != 2) {
              // if version was not specified - consider data as invalid
              return null
            }

            return doRead(objectClass, originalType, reader, configuration)
          }
          else -> LOG.warn("Unknown field: $fieldName (file=$file, expectedVersion=$expectedVersion, objectClass=$objectClass)")
        }
      }
      reader.stepOut()

      return null
    }
  }

  fun write(obj: Any, outputStream: OutputStream, configuration: WriteConfiguration = defaultWriteConfiguration, originalType: Type? = null) {
    createIonWriterBuilder(configuration.binary).build(outputStream).use { writer ->
      doWrite(obj, writer, configuration, originalType)
    }
  }

  private fun doWrite(obj: Any, writer: IonWriter, configuration: WriteConfiguration, originalType: Type?) {
    val aClass = obj.javaClass
    val writeContext = WriteContext(writer, configuration.filter ?: DEFAULT_FILTER, ObjectIdWriter(), configuration, bindingProducer)
    bindingProducer.getRootBinding(aClass, originalType ?: aClass).serialize(obj, writeContext)
  }

  fun <T> read(objectClass: Class<T>, reader: ValueReader, configuration: ReadConfiguration, originalType: Type? = null): T {
    reader.use {
      reader.next()
      return doRead(objectClass, originalType, reader, configuration)
    }
  }

  // reader cursor must be already pointed to struct
  private fun <T> doRead(objectClass: Class<T>, originalType: Type?, reader: ValueReader, configuration: ReadConfiguration): T {
    when (reader.type) {
      IonType.NULL -> throw SerializationException("root value is null")
      null -> throw SerializationException("empty input")
      else -> {
        val binding = bindingProducer.getRootBinding(objectClass, originalType ?: objectClass)
        @Suppress("UNCHECKED_CAST")
        return binding.deserialize(createReadContext(reader, configuration)) as T
      }
    }
  }

  fun <T> readList(itemClass: Class<T>, reader: ValueReader, configuration: ReadConfiguration): List<T> {
    @Suppress("UNCHECKED_CAST")
    return read(List::class.java, reader, configuration, ParameterizedTypeImpl(List::class.java, itemClass)) as List<T>
  }

  private fun createReadContext(reader: ValueReader, configuration: ReadConfiguration): ReadContext {
    return ReadContextImpl(reader, ObjectIdReader(), bindingProducer, configuration)
  }
}

private val DEFAULT_FILTER = object : SerializationFilter {
  override fun isSkipped(value: Any?) = false
}

private data class ReadContextImpl(override val reader: ValueReader,
                                   override val objectIdReader: ObjectIdReader,
                                   override val bindingProducer: BindingProducer<RootBinding>,
                                   override val configuration: ReadConfiguration) : ReadContext {
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

  override fun createSubContext(reader: ValueReader) = ReadContextImpl(reader, objectIdReader, bindingProducer, configuration)
}

private val binaryWriterBuilder by lazy { IonBinaryWriterBuilder.standard().immutable() }
private val textWriterBuilder by lazy {
  // line separator is not configurable and platform-dependent (https://github.com/amzn/ion-java/issues/57)
  IonTextWriterBuilder.pretty().immutable()
}

private fun createIonWriterBuilder(binary: Boolean): IonWriterBuilder {
  return when {
    binary -> binaryWriterBuilder
    else -> textWriterBuilder
  }
}