// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.trace.IdGenerator
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

// https://github.com/jaegertracing/jaeger-ui/issues/381
@Internal
class JaegerJsonSpanExporter(
  val file: Path,
  serviceName: String,
  serviceVersion: String? = null,
  serviceNamespace: String? = null,
) : AsyncSpanExporter {
  private val writer = JsonFactory().createGenerator(Files.newBufferedWriter(file))

  init {
    beginWriter(writer, serviceName, serviceVersion, serviceNamespace)
  }

  override suspend fun export(spans: Collection<SpanData>) {
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

  override fun shutdown() {
    // close spans
    writer.writeEndArray()
    // close data item object
    writer.writeEndObject()
    // close data
    writer.writeEndArray()
    // close root object
    writer.writeEndObject()
    writer.close()
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
    if ((k as AttributeKey<*>).key == "_CES_") {
      return@forEach
    }

    w.writeStartObject()
    w.writeStringField("key", k.key)
    w.writeStringField("type", k.type.name.lowercase(Locale.getDefault()))
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