// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.IdGenerator
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// https://github.com/jaegertracing/jaeger-ui/issues/381
class JaegerJsonSpanExporter : SpanExporter {
  companion object {
    private val writer = AtomicReference<JsonGenerator?>()

    @Volatile
    private var file: Path? = null

    fun setOutput(
      file: Path,
      serviceName: String,
      serviceVersion: String? = null,
      serviceNamespace: String? = null,
    ) {
      writer.getAndSet(null)?.let {
        finishWriter(it)
      }

      val w = JsonFactory().createGenerator(Files.newBufferedWriter(file))
      writer.set(w)
      Companion.file = file
      synchronized(w) {
        beginWriter(w, serviceName, serviceVersion, serviceNamespace)
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

    fun finish(): Path? {
      val w = writer.getAndSet(null) ?: return null
      val f = file
      finishWriter(w)
      file = null
      return f
    }

    private fun finishWriter(w: JsonGenerator) {
      synchronized(w) {
        // close spans
        w.writeEndArray()

        // close data item object
        w.writeEndObject()

        // close data
        w.writeEndArray()
        // close root object
        w.writeEndObject()
        w.close()
      }
    }
  }

  override fun export(spans: Collection<SpanData>): CompletableResultCode {
    val w = writer.get() ?: return CompletableResultCode.ofSuccess()
    synchronized(w) {
      doExport(spans, w)
    }
    return CompletableResultCode.ofSuccess()
  }

  private fun doExport(spans: Collection<SpanData>, w: JsonGenerator) {
    for (span in spans) {
      w.writeStartObject()
      w.writeStringField("traceID", span.traceId)
      w.writeStringField("spanID", span.spanId)
      w.writeStringField("operationName", span.name)
      w.writeStringField("processID", "p1")
      w.writeNumberField("startTime", TimeUnit.NANOSECONDS.toMicros(span.startEpochNanos))
      w.writeNumberField("duration", TimeUnit.NANOSECONDS.toMicros(span.endEpochNanos - span.startEpochNanos))
      val parentContext = span.parentSpanContext
      val hasError = span.status.statusCode == StatusData.error().statusCode

      val attributes = span.attributes
      if (!attributes.isEmpty || hasError) {
        w.writeArrayFieldStart("tags")
        if (hasError) {
          w.writeStartObject()
          w.writeStringField("key", "otel.status_code")
          w.writeStringField("type", "string")
          w.writeStringField("value", "ERROR")
          w.writeEndObject()
          w.writeStartObject()
          w.writeStringField("key", "error")
          w.writeStringField("type", "bool")
          w.writeBooleanField("value", true)
          w.writeEndObject()
        }
        writeAttributesAsJson(w, attributes)
        w.writeEndArray()
      }

      val events = span.events
      if (!events.isEmpty()) {
        w.writeArrayFieldStart("logs")
        for (event in events) {
          w.writeStartObject()
          w.writeNumberField("timestamp", TimeUnit.NANOSECONDS.toMicros(event.epochNanos))
          w.writeArrayFieldStart("fields")

          // event name as event attribute
          w.writeStartObject()
          w.writeStringField("key", "event")
          w.writeStringField("type", "string")
          w.writeStringField("value", event.name)
          w.writeEndObject()
          writeAttributesAsJson(w, event.attributes)
          w.writeEndArray()
          w.writeEndObject()
        }
        w.writeEndArray()
      }

      if (parentContext.isValid) {
        w.writeArrayFieldStart("references")
        w.writeStartObject()
        w.writeStringField("refType", "CHILD_OF")
        w.writeStringField("traceID", parentContext.traceId)
        w.writeStringField("spanID", parentContext.spanId)
        w.writeEndObject()
        w.writeEndArray()
      }
      w.writeEndObject()
    }
  }

  override fun flush(): CompletableResultCode {
    writer.get()?.let {
      synchronized(it) {
        it.flush()
      }
    }
    return CompletableResultCode.ofSuccess()
  }

  override fun shutdown(): CompletableResultCode {
    writer.getAndSet(null)?.let(Companion::finishWriter)
    return CompletableResultCode.ofSuccess()
  }
}