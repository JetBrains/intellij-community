// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import groovy.transform.CompileStatic
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.IdGenerator
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

// https://github.com/jaegertracing/jaeger-ui/issues/381
@CompileStatic
final class JaegerJsonSpanExporter implements SpanExporter {
  private static final AtomicReference<JsonGenerator> writer = new AtomicReference<>()
  private static volatile Path file
  private static final AtomicBoolean shutdownHookAdded = new AtomicBoolean()

  static void setOutput(@NotNull Path file) {
    JsonGenerator w = writer.getAndSet(null)
    if (w != null) {
      finishWriter(w)
    }

    if (shutdownHookAdded.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        @Override
        void run() {
          SdkTracerProvider tracerProvider = TracerProviderManager.tracerProvider
          if (tracerProvider != null) {
            TracerProviderManager.tracerProvider = null
            tracerProvider.close()
          }
        }
      }, "close tracer"))
    }

    w = new JsonFactory().createGenerator(Files.newBufferedWriter(file)).useDefaultPrettyPrinter()
    writer.set(w)
    JaegerJsonSpanExporter.file = file
    w.writeStartObject()
    w.writeArrayFieldStart("data")

    w.writeStartObject()
    w.writeStringField("traceID", IdGenerator.random().generateTraceId())

    // process info
    w.writeObjectFieldStart("processes")
    w.writeObjectFieldStart("p1")
    w.writeStringField("serviceName", "build")

    w.writeArrayFieldStart("tags")

    w.writeStartObject()
    w.writeStringField("key", "time")
    w.writeStringField("type", "string",)
    w.writeStringField("value", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
    w.writeEndObject()

    w.writeEndArray()

    w.writeEndObject()
    w.writeEndObject()

    w.writeArrayFieldStart("spans")
  }

  @Override
  CompletableResultCode export(Collection<SpanData> spans) {
    JsonGenerator w = writer.get()
    if (w == null) {
      return CompletableResultCode.ofSuccess()
    }

    for (SpanData span : spans) {
      w.writeStartObject()
      w.writeStringField("traceID", span.getTraceId())
      w.writeStringField("spanID", span.getSpanId())
      w.writeStringField("operationName", span.getName())
      w.writeStringField("processID", "p1")
      w.writeNumberField("startTime", TimeUnit.NANOSECONDS.toMicros(span.getStartEpochNanos()))
      w.writeNumberField("duration", TimeUnit.NANOSECONDS.toMicros(span.getEndEpochNanos() - span.getStartEpochNanos()))

      SpanContext parentContext = span.parentSpanContext

      boolean hasError = span.status.statusCode == StatusData.error().statusCode
      Attributes attributes = span.getAttributes()
      if (!attributes.isEmpty() || hasError) {
        w.writeArrayFieldStart("tags")

        if (hasError) {
          w.writeStartObject()
          //noinspection SpellCheckingInspection
          w.writeStringField("key", "otel.status_code")
          w.writeStringField("type", "string")
          w.writeStringField("value", "ERROR")
          w.writeEndObject()

          w.writeStartObject()
          //noinspection SpellCheckingInspection
          w.writeStringField("key", "error")
          w.writeStringField("type", "bool")
          w.writeBooleanField("value", true)
          w.writeEndObject()
        }

        writeAttributesAsJson(w, attributes)
        w.writeEndArray()
      }

      List<EventData> events = span.getEvents()
      if (!events.isEmpty()) {
        w.writeArrayFieldStart("logs")
        for (EventData event : events) {
          w.writeStartObject()

          w.writeNumberField("timestamp", TimeUnit.NANOSECONDS.toMicros(event.getEpochNanos()))
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

      if (parentContext.isValid()) {
        w.writeArrayFieldStart("references")
        w.writeStartObject()

        w.writeStringField("refType", "CHILD_OF")
        w.writeStringField("traceID", parentContext.getTraceId())
        w.writeStringField("spanID", parentContext.getSpanId())

        w.writeEndObject()
        w.writeEndArray()
      }

      w.writeEndObject()
    }

    return CompletableResultCode.ofSuccess()
  }

  private static void writeAttributesAsJson(JsonGenerator w, Attributes attributes) {
    attributes.forEach({ k, v ->
      if (k.key == "_CES_") {
        return
      }

      w.writeStartObject()
      w.writeStringField("key", k.key)
      w.writeStringField("type", k.type.name().toLowerCase())
      if (v instanceof Iterable) {
        w.writeArrayFieldStart("value")
        for (String item : (Iterable<String>)v) {
          w.writeString(item)
        }
        w.writeEndArray()
      }
      else {
        w.writeStringField("value", v.toString())
      }
      w.writeEndObject()
    })
  }

  @Override
  CompletableResultCode flush() {
    writer.get()?.flush()
    return CompletableResultCode.ofSuccess()
  }

  @Override
  CompletableResultCode shutdown() {
    JsonGenerator w = writer.getAndSet(null)
    if (w != null) {
      finishWriter(w)
    }
    return CompletableResultCode.ofSuccess()
  }

  @Nullable
  protected static Path finish(@Nullable SdkTracerProvider tracerProvider) {
    JsonGenerator w = writer.getAndSet(null)
    if (w == null) {
      return null
    }

    Path f = file
    tracerProvider?.forceFlush()?.join(10, TimeUnit.SECONDS)
    finishWriter(w)
    file = null
    return f
  }

  private static synchronized void finishWriter(@NotNull JsonGenerator w) {
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
