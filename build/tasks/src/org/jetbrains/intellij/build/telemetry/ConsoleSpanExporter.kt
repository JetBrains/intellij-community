// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package org.jetbrains.intellij.build.telemetry

import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.SpanData
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.LongArrayList
import org.jetbrains.annotations.Contract
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

private fun createPathList(dir: Path): List<String> {
  val s1 = dir.toString() + File.separatorChar
  val s2 = dir.toAbsolutePath().normalize().toString() + File.separatorChar
  return if (s1 == s2) java.util.List.of(s1) else java.util.List.of(s1, s2)
}

class ConsoleSpanExporter : AsyncSpanExporter {
  private val isEnabled = System.getProperty("intellij.build.console.exporter.enabled")?.toBoolean() ?: true

  companion object {
    fun setPathRoot(dir: Path) {
      rootPathsWithEndSlash = createPathList(dir)
    }
  }

  override suspend fun export(spans: Collection<SpanData>) {
    if (!isEnabled) return
    val sb = StringBuilder()
    for (span in spans) {
      writeSpan(sb, span, span.endEpochNanos - span.startEpochNanos, span.endEpochNanos)
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
private var m2PathsWithEndSlash = createPathList(Path.of(System.getProperty("user.home"), ".m2/repository"))

private fun writeSpan(sb: StringBuilder, span: SpanData, duration: Long, endEpochNanos: Long) {
  sb.append(span.name)
  sb.append(" (duration=")
  sb.append(formatDuration(duration / 1_000_000))
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

private val m2Macro = "\$MAVEN_REPOSITORY\$" + File.separatorChar

private fun writeValueAsHumanReadable(s: String, sb: StringBuilder) {
  for (prefix in rootPathsWithEndSlash) {
    val newS = s.replace(prefix, "")
    if (newS !== s) {
      sb.append(newS)
      return
    }
  }
  for (prefix in m2PathsWithEndSlash) {
    val newS = s.replace(prefix, m2Macro)
    if (newS !== s) {
      sb.append(newS)
      return
    }
  }
  sb.append(s)
}

private val THREAD_NAME = AttributeKey.stringKey("thread.name")
private val THREAD_ID = AttributeKey.longKey("thread.id")
private val EXCEPTION_STACKTRACE = AttributeKey.stringKey("exception.stacktrace")

private val NEW_LINE_REGEX = "(\r\n|\n)".toRegex()

private fun writeAttributesAsHumanReadable(attributes: Attributes, sb: StringBuilder, writeFirstComma: Boolean) {
  var writeComma = writeFirstComma
  attributes.forEach(BiConsumer { k, v ->
    if (k == THREAD_NAME || k == THREAD_ID) {
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
    if (k == EXCEPTION_STACKTRACE) {
      val delimiter = "─".repeat(79)
      sb.append("\n  ┌")
      sb.append(delimiter)
      sb.append("┐\n   ")
      sb.append(v.toString().replace(NEW_LINE_REGEX, "\n   ").trim())
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

private val TIME_UNITS = arrayOf("ms", "s", "m", "h", "d")
private val TIME_MULTIPLIERS = longArrayOf(1, 1000, 60, 60, 24)

// cannot depend on Formats, and kotlin Duration.toString() is not good (`8.515s` instead of `8 s 515 ms`)
@Contract(pure = true)
private fun formatDuration(duration: Long): java.lang.StringBuilder {
  val unitValues = LongArrayList()
  val unitIndices = IntArrayList()
  var count = duration
  var i = 1
  while (i < TIME_UNITS.size && count > 0) {
    val multiplier = TIME_MULTIPLIERS[i]
    if (count < multiplier) break
    val remainder = count % multiplier
    count /= multiplier
    if (remainder != 0L || !unitValues.isEmpty) {
      unitValues.add(0, remainder)
      unitIndices.add(0, i - 1)
    }
    i++
  }
  unitValues.add(0, count)
  unitIndices.add(0, i - 1)
  val result = java.lang.StringBuilder()
  i = 0
  while (i < unitValues.size) {
    if (i > 0) result.append(" ")
    result.append(unitValues.getLong(i)).append(' ').append(TIME_UNITS[unitIndices.getInt(i)])
    i++
  }
  return result
}