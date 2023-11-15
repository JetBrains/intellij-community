// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.openapi.application.PathManager
import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.trace.IdGenerator
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// https://github.com/jaegertracing/jaeger-ui/issues/381
@ApiStatus.Internal
class JaegerJsonSpanExporter(
  val file: Path,
  serviceName: String,
  serviceVersion: String? = null,
  serviceNamespace: String? = null,
) : AsyncSpanExporter {
  private val tempTelemetryPath: Path
  private val telemetryJsonPath: Path
  private val writer: JsonGenerator

  private val lock = Mutex()

  init {
    // presume that telemetry stuff needs to be saved in log dir
    if (!file.isAbsolute || file.parent == null) {
      Files.createDirectories(PathManager.getLogDir().toAbsolutePath()).apply {
        tempTelemetryPath = this.resolve("telemetry.temp")
        telemetryJsonPath = this.resolve(file)
      }
    }
    // path is absolute and has a parent
    else {
      tempTelemetryPath = Files.createDirectories(file.parent.toAbsolutePath()).resolve("telemetry.temp").toAbsolutePath()
      telemetryJsonPath = file
    }

    writer = JsonFactory().createGenerator(Files.newBufferedWriter(tempTelemetryPath))
      .configure(com.fasterxml.jackson.core.JsonGenerator.Feature.AUTO_CLOSE_TARGET, true)

    beginWriter(w = writer, serviceName = serviceName, serviceVersion = serviceVersion, serviceNamespace = serviceNamespace)
  }

  @Suppress("DuplicatedCode")
  override suspend fun export(spans: Collection<SpanData>) {
    lock.withReentrantLock {
      for (span in spans) {
        writer.writeStartObject()
        writer.writeStringField("traceID", span.traceId)
        writer.writeStringField("spanID", span.spanId)
        writer.writeStringField("operationName", span.name)
        writer.writeStringField("processID", "p1")
        writer.writeNumberField("startTime", TimeUnit.NANOSECONDS.toMicros(span.startEpochNanos))
        writer.writeNumberField("duration", TimeUnit.NANOSECONDS.toMicros(span.endEpochNanos - span.startEpochNanos))
        val parentContext = span.parentSpanContext
        val hasError = span.status.statusCode == StatusData.error().statusCode

        val attributes = span.attributes
        if (!attributes.isEmpty || hasError) {
          writer.writeArrayFieldStart("tags")
          if (hasError) {
            writer.writeStartObject()
            writer.writeStringField("key", "otel.status_code")
            writer.writeStringField("type", "string")
            writer.writeStringField("value", "ERROR")
            writer.writeEndObject()
            writer.writeStartObject()
            writer.writeStringField("key", "error")
            writer.writeStringField("type", "bool")
            writer.writeBooleanField("value", true)
            writer.writeEndObject()
          }
          writeAttributesAsJson(writer, attributes)
          writer.writeEndArray()
        }

        val events = span.events
        if (!events.isEmpty()) {
          writer.writeArrayFieldStart("logs")
          for (event in events) {
            writer.writeStartObject()
            writer.writeNumberField("timestamp", TimeUnit.NANOSECONDS.toMicros(event.epochNanos))
            writer.writeArrayFieldStart("fields")

            // event name as event attribute
            writer.writeStartObject()
            writer.writeStringField("key", "event")
            writer.writeStringField("type", "string")
            writer.writeStringField("value", event.name)
            writer.writeEndObject()
            writeAttributesAsJson(writer, event.attributes)
            writer.writeEndArray()
            writer.writeEndObject()
          }
          writer.writeEndArray()
        }

        if (parentContext.isValid) {
          writer.writeArrayFieldStart("references")
          writer.writeStartObject()
          writer.writeStringField("refType", "CHILD_OF")
          writer.writeStringField("traceID", parentContext.traceId)
          writer.writeStringField("spanID", parentContext.spanId)
          writer.writeEndObject()
          writer.writeEndArray()
        }
        writer.writeEndObject()
      }
      writer.flush()
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  @Suppress("DuplicatedCode")
  suspend fun flushOtlp(scopeSpans: Collection<ScopeSpans>) {
    lock.withReentrantLock {
      for (scopeSpan in scopeSpans) {
        for (span in scopeSpan.spans) {
          writer.writeStartObject()
          val traceId = span.traceId.toHexString()
          writer.writeStringField("traceID", traceId)
          writer.writeStringField("spanID", span.spanId.toHexString())
          writer.writeStringField("operationName", span.name)

          writer.writeStringField("processID", "p1")
          writer.writeNumberField("startTime", TimeUnit.NANOSECONDS.toMicros(span.startTimeUnixNano))
          writer.writeNumberField("duration", TimeUnit.NANOSECONDS.toMicros(span.endTimeUnixNano - span.startTimeUnixNano))

          val attributes = span.attributes
          if (!attributes.isEmpty()) {
            writer.writeArrayFieldStart("tags")
            for (k in attributes) {
              val w = writer
              w.writeStartObject()
              w.writeStringField("key", k.key)
              w.writeStringField("type", "string")
              w.writeStringField("value", k.value.string)
              w.writeEndObject()
            }
            writer.writeEndArray()
          }

          if (span.parentSpanId != null) {
            writer.writeArrayFieldStart("references")
            writer.writeStartObject()
            writer.writeStringField("refType", "CHILD_OF")
            // not an error - space trace id equals to parent, OpenTelemetry opposite to Jaeger doesn't support cross-trace parent
            writer.writeStringField("traceID", traceId)
            writer.writeStringField("spanID", span.parentSpanId.toHexString())
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
        closeJsonFile(writer)
        // nothing was written to the file
        if (Files.notExists(tempTelemetryPath)) {
          return@withContext
        }

        Files.move(tempTelemetryPath, telemetryJsonPath, StandardCopyOption.REPLACE_EXISTING)
      }
    }
  }

  override suspend fun flush() {
    lock.withReentrantLock {
      // if shutdown was already invoked OR nothing has been written to the temp file
      if (writer.isClosed || Files.notExists(tempTelemetryPath)) {
        return@withReentrantLock
      }

      Files.copy(tempTelemetryPath, telemetryJsonPath, StandardCopyOption.REPLACE_EXISTING)
      closeJsonFile(Files.newBufferedWriter(file, StandardOpenOption.APPEND))
    }
  }
}

private fun beginWriter(w: JsonGenerator,
                        serviceName: String,
                        serviceVersion: String?,
                        serviceNamespace: String?) {
  w.writeStartObject()
  w.writeArrayFieldStart("data")
  w.writeStartObject()
  w.writeStringField("traceID", IdGenerator.random().generateTraceId())

  // process info
  w.writeObjectFieldStart("processes")
  w.writeObjectFieldStart("p1")
  w.writeStringField("serviceName", serviceName)

  w.writeArrayFieldStart("tags")

  w.writeStartObject()
  w.writeStringField("key", "time")
  w.writeStringField("type", "string")
  w.writeStringField("value", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
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
  w.writeArrayFieldStart("spans")
}

private fun writeStringTag(name: String, value: String, w: JsonGenerator) {
  w.writeStartObject()
  w.writeStringField("key", name)
  w.writeStringField("type", "string")
  w.writeStringField("value", value)
  w.writeEndObject()
}

private fun writeAttributesAsJson(w: JsonGenerator, attributes: Attributes) {
  attributes.forEach { k, v ->
    w.writeStartObject()
    w.writeStringField("key", k.key)
    w.writeStringField("type", k.type.name.lowercase())
    if (v is Iterable<*>) {
      w.writeArrayFieldStart("value")
      for (item in v) {
        w.writeString(item as String)
      }
      w.writeEndArray()
    }
    else {
      w.writeStringField("value", v.toString())
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

private fun closeJsonFile(writer: BufferedWriter) {
  // JsonGenerator created from scratch can't close unfinished a json object
  writer.use { it.append("]}]}") }
}