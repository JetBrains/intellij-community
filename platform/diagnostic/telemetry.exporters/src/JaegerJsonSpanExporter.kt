// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters

import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.trace.IdGenerator
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import tools.jackson.core.JsonGenerator
import tools.jackson.core.ObjectWriteContext
import tools.jackson.core.StreamWriteFeature
import tools.jackson.core.json.JsonFactory
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.EnumSet
import java.util.concurrent.TimeUnit

// https://github.com/jaegertracing/jaeger-ui/issues/381
@ApiStatus.Internal
class JaegerJsonSpanExporter(
  file: Path,
  val serviceName: String,
  val serviceVersion: String? = null,
  val serviceNamespace: String? = null,
) : AsyncSpanExporter {
  private val fileChannel: FileChannel
  private var writer: JsonGenerator

  private val lock = Mutex()

  private fun initWriter() = JsonFactory().createGenerator(ObjectWriteContext.empty(), Channels.newOutputStream(fileChannel))
    .configure(StreamWriteFeature.AUTO_CLOSE_TARGET, true)
    // Channels.newOutputStream doesn't implement flush, but just to be sure
    .configure(StreamWriteFeature.FLUSH_PASSED_TO_STREAM, false)

  init {
    val parent = file.parent
    Files.createDirectories(parent)

    fileChannel = FileChannel.open(file, EnumSet.of(StandardOpenOption.CREATE,
                                                    StandardOpenOption.WRITE,
                                                    StandardOpenOption.TRUNCATE_EXISTING))

    writer = initWriter()

    beginWriter(w = writer,
                serviceName = serviceName,
                serviceVersion = serviceVersion,
                serviceNamespace = serviceNamespace)
  }

  @Suppress("DuplicatedCode")
  override suspend fun export(spans: Collection<SpanData>) {
    lock.withReentrantLock {
      for (span in spans) {
        writer.writeStartObject()
        writer.writeStringProperty("traceID", span.traceId)
        writer.writeStringProperty("spanID", span.spanId)
        writer.writeStringProperty("operationName", span.name)
        writer.writeStringProperty("processID", "p1")
        writer.writeNumberProperty("startTime", TimeUnit.NANOSECONDS.toMicros(span.startEpochNanos)) // in microseconds (Jaeger format)
        writer.writeNumberProperty("duration", TimeUnit.NANOSECONDS.toMicros(span.endEpochNanos - span.startEpochNanos)) // // in microseconds (Jaeger format)
        writer.writeNumberProperty("startTimeNano", span.startEpochNanos) // in nanoseconds
        writer.writeNumberProperty("durationNano", span.endEpochNanos - span.startEpochNanos) // in nanoseconds

        val parentContext = span.parentSpanContext
        val hasError = span.status.statusCode == StatusData.error().statusCode

        val attributes = span.attributes
        if (!attributes.isEmpty || hasError) {
          writer.writeArrayPropertyStart("tags")
          if (hasError) {
            writer.writeStartObject()
            writer.writeStringProperty("key", "otel.status_code")
            writer.writeStringProperty("type", "string")
            writer.writeStringProperty("value", "ERROR")
            writer.writeEndObject()
            writer.writeStartObject()
            writer.writeStringProperty("key", "error")
            writer.writeStringProperty("type", "boolean")
            writer.writeStringProperty("value", "true")
            writer.writeEndObject()
          }
          writeAttributesAsJson(writer, attributes)
          writer.writeEndArray()
        }

        val events = span.events
        if (!events.isEmpty()) {
          writer.writeArrayPropertyStart("logs")
          for (event in events) {
            writer.writeStartObject()
            writer.writeNumberProperty("timestamp", TimeUnit.NANOSECONDS.toMicros(event.epochNanos))
            writer.writeArrayPropertyStart("fields")

            // event name as event attribute
            writer.writeStartObject()
            writer.writeStringProperty("key", "event")
            writer.writeStringProperty("type", "string")
            writer.writeStringProperty("value", event.name)
            writer.writeEndObject()
            writeAttributesAsJson(writer, event.attributes)
            writer.writeEndArray()
            writer.writeEndObject()
          }
          writer.writeEndArray()
        }

        if (parentContext.isValid) {
          writer.writeArrayPropertyStart("references")
          writer.writeStartObject()
          writer.writeStringProperty("refType", "CHILD_OF")
          writer.writeStringProperty("traceID", parentContext.traceId)
          writer.writeStringProperty("spanID", parentContext.spanId)
          writer.writeEndObject()
          writer.writeEndArray()
        }
        writer.writeEndObject()
      }
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  @Suppress("DuplicatedCode")
  suspend fun flushOtlp(scopeSpans: Collection<ScopeSpans>) {
    lock.withReentrantLock {
      assert(!writer.isClosed)

      for (scopeSpan in scopeSpans) {
        for (span in scopeSpan.spans) {
          writer.writeStartObject()
          val traceId = span.traceId.toHexString()
          writer.writeStringProperty("traceID", traceId)
          writer.writeStringProperty("spanID", span.spanId.toHexString())
          writer.writeStringProperty("operationName", span.name)

          writer.writeStringProperty("processID", "p1")
          writer.writeNumberProperty("startTime", TimeUnit.NANOSECONDS.toMicros(span.startTimeUnixNano))
          writer.writeNumberProperty("duration", TimeUnit.NANOSECONDS.toMicros(span.endTimeUnixNano - span.startTimeUnixNano))

          val attributes = span.attributes
          if (!attributes.isEmpty()) {
            writer.writeArrayPropertyStart("tags")
            for (k in attributes) {
              val w = writer
              w.writeStartObject()
              w.writeStringProperty("key", k.key)
              w.writeStringProperty("type", "string")
              w.writeStringProperty("value", k.value.string)
              w.writeEndObject()
            }
            writer.writeEndArray()
          }

          if (span.parentSpanId != null) {
            writer.writeArrayPropertyStart("references")
            writer.writeStartObject()
            writer.writeStringProperty("refType", "CHILD_OF")
            // not an error - space trace id equals to parent, OpenTelemetry opposite to Jaeger doesn't support cross-trace parent
            writer.writeStringProperty("traceID", traceId)
            writer.writeStringProperty("spanID", span.parentSpanId.toHexString())
            writer.writeEndObject()
            writer.writeEndArray()
          }
          writer.writeEndObject()
        }
      }
      writer.flush()
    }
  }

  override suspend fun shutdown() {
    lock.withReentrantLock {
      withContext(Dispatchers.IO) {
        fileChannel.use {
          closeJsonFile(writer)
        }
      }
    }
  }

  override suspend fun flush() {
    lock.withReentrantLock {
      // if shutdown was already invoked OR nothing has been written to the output file
      if (writer.isClosed) {
        return@withReentrantLock
      }

      runInterruptible {
        runBlocking(Dispatchers.IO) {
          writer.flush()
          fileChannel.write(ByteBuffer.wrap(jsonEnd))
          fileChannel.force(false)
          fileChannel.position(fileChannel.position() - jsonEnd.size)
        }
      }
    }
  }

  override suspend fun reset() {
    lock.withReentrantLock {
      // if shutdown was already invoked OR nothing has been written to the output file
      if (writer.isClosed) {
        return@withReentrantLock
      }

      writer = initWriter()
      runInterruptible {
        runBlocking(Dispatchers.IO) {
          fileChannel.truncate(0)

          beginWriter(w = writer,
                      serviceName = serviceName,
                      serviceVersion = serviceVersion,
                      serviceNamespace = serviceNamespace)
        }
      }
    }
  }
}

private fun beginWriter(
  w: JsonGenerator,
  serviceName: String,
  serviceVersion: String?,
  serviceNamespace: String?,
) {
  w.writeStartObject()
  w.writeArrayPropertyStart("data")
  w.writeStartObject()
  w.writeStringProperty("traceID", IdGenerator.random().generateTraceId())

  // process info
  w.writeObjectPropertyStart("processes")
  w.writeObjectPropertyStart("p1")
  w.writeStringProperty("serviceName", serviceName)

  w.writeArrayPropertyStart("tags")

  w.writeStartObject()
  w.writeStringProperty("key", "time")
  w.writeStringProperty("type", "string")
  w.writeStringProperty("value", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
  w.writeEndObject()

  if (serviceVersion != null) {
    writeStringTag("service.version", serviceVersion, w)
  }
  if (serviceNamespace != null) {
    writeStringTag("service.namespace", serviceNamespace, w)
  }

  w.writeEndArray()

  w.writeEndObject()
  w.writeEndObject()
  w.writeArrayPropertyStart("spans")
}

private fun writeStringTag(name: String, value: String, w: JsonGenerator) {
  w.writeStartObject()
  w.writeStringProperty("key", name)
  w.writeStringProperty("type", "string")
  w.writeStringProperty("value", value)
  w.writeEndObject()
}

private fun writeAttributesAsJson(w: JsonGenerator, attributes: Attributes) {
  attributes.forEach { k, v ->
    w.writeStartObject()
    w.writeStringProperty("key", k.key)
    w.writeStringProperty("type", k.type.name.lowercase())
    if (v is Iterable<*>) {
      w.writeArrayPropertyStart("value")
      for (item in v) {
        w.writeString(item as String)
      }
      w.writeEndArray()
    }
    else {
      w.writeStringProperty("value", v.toString())
    }
    w.writeEndObject()
  }
}


private fun closeJsonFile(jsonGenerator: JsonGenerator) {
  // close spans
  jsonGenerator.writeEndArray()
  // close data item object
  jsonGenerator.writeEndObject()
  // close data
  jsonGenerator.writeEndArray()
  // close the root object
  jsonGenerator.writeEndObject()
  jsonGenerator.close()
}

private val jsonEnd = "]}]}".encodeToByteArray()
