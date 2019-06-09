// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonException
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter
import com.amazon.ion.impl.bin._Private_IonManagedBinaryWriterBuilder
import com.amazon.ion.system.IonReaderBuilder
import com.amazon.ion.system.IonTextWriterBuilder
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.ParameterizedTypeImpl
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type
import java.nio.file.Path
import kotlin.experimental.or

private const val FORMAT_VERSION = 2

internal class IonObjectSerializer {
  val readerBuilder: IonReaderBuilder = IonReaderBuilder.standard().immutable()

  // by default only fields (including private)
  private val propertyCollector = PropertyCollector(PropertyCollector.COLLECT_PRIVATE_FIELDS or PropertyCollector.COLLECT_FINAL_FIELDS)

  internal val bindingProducer = IonBindingProducer(propertyCollector)

  @Throws(IOException::class)
  fun writeVersioned(obj: Any, out: OutputStream, expectedVersion: Int, configuration: WriteConfiguration = defaultWriteConfiguration, originalType: Type? = null) {
    createIonWriterBuilder(configuration.binary, out).use { writer ->
      writer.stepIn(IonType.STRUCT)

      writer.setFieldName("version")
      writer.writeInt(expectedVersion.toLong())

      writer.setFieldName("formatVersion")
      writer.writeInt(FORMAT_VERSION.toLong())

      writer.setFieldName("data")
      doWrite(obj, writer, configuration, originalType)

      writer.stepOut()
    }
  }

  @Throws(IOException::class)
  fun <T : Any> readVersioned(objectClass: Class<T>, input: InputStream, inputName: Path, expectedVersion: Int, configuration: ReadConfiguration, originalType: Type? = null): T? {
    readerBuilder.build(input).use { reader ->
      @Suppress("UNUSED_VARIABLE")
      var isVersionChecked = 0

      fun logVersionMismatch(prefix: String, currentVersion: Int) {
        LOG.info("$prefix version mismatch (file=$inputName, currentVersion: $currentVersion, expectedVersion=$expectedVersion, objectClass=$objectClass)")
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

            val context = createReadContext(reader, configuration)
            try {
              return doRead(objectClass, originalType, context)
            }
            finally {
              context.errors.report(LOG)
            }
          }
          else -> LOG.warn("Unknown field: $fieldName (file=$inputName, expectedVersion=$expectedVersion, objectClass=$objectClass)")
        }
      }
      reader.stepOut()

      return null
    }
  }

  fun write(obj: Any, out: OutputStream, configuration: WriteConfiguration = defaultWriteConfiguration, originalType: Type? = null) {
    createIonWriterBuilder(configuration.binary, out).use { writer ->
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
      val context = createReadContext(reader, configuration)
      try {
        return doRead(objectClass, originalType, context)
      }
      finally {
        context.errors.report(LOG)
      }
    }
  }

  // reader cursor must be already pointed to struct
  private fun <T> doRead(objectClass: Class<T>, originalType: Type?, context: ReadContext): T {
    when (context.reader.type) {
      IonType.NULL -> throw SerializationException("root value is null")
      null -> throw SerializationException("empty input")
      else -> {
        val binding = bindingProducer.getRootBinding(objectClass, originalType ?: objectClass)
        @Suppress("UNCHECKED_CAST")
        return binding.deserialize(context) as T
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
                                   override val bindingProducer: BindingProducer,
                                   override val configuration: ReadConfiguration) : ReadContext {
  private var byteArrayOutputStream: BufferExposingByteArrayOutputStream? = null

  override val errors = ReadErrors()

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

internal val binaryWriterBuilder by lazy {
  val binaryWriterBuilder = _Private_IonManagedBinaryWriterBuilder
    .create(PooledBlockAllocatorProvider())
    .withPaddedLengthPreallocation(0)
    .withStreamCopyOptimization(true)
  binaryWriterBuilder
}

private val textWriterBuilder by lazy {
  // line separator is not configurable and platform-dependent (https://github.com/amzn/ion-java/issues/57)
  IonTextWriterBuilder.pretty().immutable()
}

private fun createIonWriterBuilder(binary: Boolean, out: OutputStream): IonWriter {
  return when {
    binary -> binaryWriterBuilder.newWriter(out)
    else -> textWriterBuilder.build(out)
  }
}