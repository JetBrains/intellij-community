// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package org.jetbrains.intellij.build

import com.intellij.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.openapi.util.text.Formats
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

class ConsoleSpanExporter : AsyncSpanExporter {
  companion object {
    fun setPathRoot(dir: Path) {
      val s1 = dir.toString() + File.separatorChar
      val s2 = dir.toRealPath().toString() + File.separatorChar
      rootPathsWithEndSlash = if (s1 == s2) java.util.List.of(s1) else java.util.List.of(s1, s2)
    }
  }

  override suspend fun export(spans: Collection<SpanData>) {
    val sb = StringBuilder()
    for (span in spans) {
      val attributes = span.attributes
      val reportSpanToConsole = attributes.get(AttributeKey.booleanKey("_CES_")) != java.lang.Boolean.TRUE
      if (reportSpanToConsole) {
        writeSpan(sb, span, span.endEpochNanos - span.startEpochNanos, span.endEpochNanos)
      }
    }
    if (sb.isNotEmpty()) {
      // System.out.print is synchronized - buffer content to reduce calls
      print(sb.toString())
    }
  }
}

private val EXCLUDED_EVENTS_FROM_CONSOLE = java.util.Set.of("include module outputs")
private val ISO_LOCAL_TIME = DateTimeFormatterBuilder()
  .appendValue(ChronoField.HOUR_OF_DAY, 2)
  .appendLiteral(":").appendValue(ChronoField.MINUTE_OF_HOUR, 2).optionalStart()
  .appendLiteral(":")
  .appendValue(ChronoField.SECOND_OF_MINUTE, 2).optionalStart()
  .appendFraction(ChronoField.NANO_OF_SECOND, 0, 4, true)
  .toFormatter()

private var rootPathsWithEndSlash = emptyList<String>()
private val buildRootMacro = "\${buildRoot}${File.separatorChar}"

private fun writeSpan(sb: StringBuilder, span: SpanData, duration: Long, endEpochNanos: Long) {
  sb.append(span.name)
  sb.append(" (duration=")
  sb.append(Formats.formatDuration(TimeUnit.NANOSECONDS.toMillis(duration)))
  sb.append(", end=")
  writeTime(endEpochNanos, sb)
  if (span.status.statusCode == StatusCode.ERROR && !span.status.description.isEmpty()) {
    sb.append(", error=")
    sb.append(span.status.description)
  }
  writeAttributesAsHumanReadable(span.attributes, sb, writeFirstComma = true)
  sb.append(')')
  sb.append('\n')
  val events = span.events
  if (!events.isEmpty()) {
    var isHeaderWritten = false
    var offset = 0
    var prefix = " "
    for (event in events) {
      if (EXCLUDED_EVENTS_FROM_CONSOLE.contains(event.name)) {
        offset--
        continue
      }

      if (!isHeaderWritten) {
        sb.append("  events:")
        if ((events.size - offset) > 1) {
          sb.append('\n')
          prefix = "    "
        }
        isHeaderWritten = true
      }
      sb.append(prefix)
      sb.append(event.name)
      sb.append(" (")
      if (!event.attributes.isEmpty) {
        writeAttributesAsHumanReadable(event.attributes, sb, writeFirstComma = false)
        sb.append(", ")
      }
      sb.append("time=")
      writeTime(event.epochNanos, sb)

      sb.append(')')
      sb.append('\n')
    }
  }
}

private fun writeTime(epochNanos: Long, sb: StringBuilder) {
  val epochSeconds = TimeUnit.NANOSECONDS.toSeconds(epochNanos)
  val adjustNanos = epochNanos - TimeUnit.SECONDS.toNanos(epochSeconds)
  ISO_LOCAL_TIME.formatTo(LocalTime.ofInstant(Instant.ofEpochSecond(epochSeconds, adjustNanos), ZoneId.systemDefault()), sb)
}

private fun writeValueAsHumanReadable(s: String, sb: StringBuilder) {
  for (prefix in rootPathsWithEndSlash) {
    val newS = s.replace(prefix, buildRootMacro)
    if (newS != s) {
      sb.append(newS)
      return
    }
  }
  sb.append(s)
}

private fun writeAttributesAsHumanReadable(attributes: Attributes, sb: StringBuilder, writeFirstComma: Boolean) {
  var writeComma = writeFirstComma
  attributes.forEach(BiConsumer { k, v ->
    if (k == SemanticAttributes.THREAD_NAME || k == SemanticAttributes.THREAD_ID) {
      return@BiConsumer
    }

    if (writeComma) {
      sb.append(", ")
    }
    else {
      writeComma = true
    }

    sb.append(k.key)
    sb.append('=')
    if (k == SemanticAttributes.EXCEPTION_STACKTRACE) {
      val delimiter = "─".repeat(79)
      sb.append("\n  ┌")
      sb.append(delimiter)
      sb.append("┐\n   ")
      sb.append(v.toString().replace("(\r\n|\n)".toRegex(), "\n   ").trim { it <= ' ' })
      sb.append("\n  └")
      sb.append(delimiter)
      sb.append("┘")
    }
    else {
      if ((k.key == "modulesWithSearchableOptions" || v is List<*>) && (v as List<*>).size > 16) {
        sb.append("…")
      }
      else if (v is Iterable<*>) {
        for (s in v) {
          writeValueAsHumanReadable(s as String, sb)
          sb.append(", ")
        }
        sb.setLength(sb.length - 2)
      }
      else {
        writeValueAsHumanReadable(v.toString(), sb)
      }
    }
  })
}