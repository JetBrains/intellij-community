// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.ThreadNameManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import java.io.CharArrayWriter
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

internal abstract class IdeaFormatWriter(private val activities: Map<String, MutableList<ActivityImpl>>, private val threadNameManager: ThreadNameManager) {
  private val logPrefix = "=== Start: StartUp Measurement ===\n"
  protected val stringWriter = ExposingCharArrayWriter()

  fun write(timeOffset: Long, items: List<ActivityImpl>, serviceActivities: Map<String, MutableList<ActivityImpl>>, instantEvents: List<ActivityImpl>, end: Long, projectName: String) {
    stringWriter.write(logPrefix)

    val writer = JsonFactory().createGenerator(stringWriter)
    writer.prettyPrinter = MyJsonPrettyPrinter()
    writer.use {
      writer.obj {
        writer.writeStringField("version", StartUpPerformanceReporter.VERSION)
        writeAppInfo(writer)
        writer.writeStringField("generated", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
        writer.writeStringField("os", SystemInfo.getOsNameAndVersion())
        writer.writeStringField("runtime", SystemInfo.JAVA_VENDOR + " " + SystemInfo.JAVA_VERSION + " " + SystemInfo.JAVA_RUNTIME_VERSION)

        writeProjectName(writer, projectName)

        writeExtraData(writer)

        writer.array("traceEvents") {
          TraceEventFormatWriter(timeOffset, instantEvents, threadNameManager).writeInstantEvents(writer)
        }

        writeServiceEvents(writer, serviceActivities, timeOffset)

        var totalDuration = 0L
        writer.array("items") {
          for ((index, item) in items.withIndex()) {
            writer.obj {
              writer.writeStringField("name", item.name)
              if (item.description != null) {
                writer.writeStringField("description", item.description)
              }

              val duration = item.end - item.start
              if (!isSubItem(item, index, items)) {
                totalDuration += duration
              }

              writeItemTimeInfo(item, duration, timeOffset, writer)
            }
          }
        }

        totalDuration += end - items.last().end

        writeParallelActivities(timeOffset, writer)

        writeTotalDuration(writer, totalDuration, end, timeOffset)
      }
    }
  }

  protected open fun writeTotalDuration(writer: JsonGenerator, totalDuration: Long, end: Long, timeOffset: Long): Long {
    writer.writeNumberField("totalDurationComputed", TimeUnit.NANOSECONDS.toMillis(totalDuration))
    val totalDurationActual = TimeUnit.NANOSECONDS.toMillis(end - timeOffset)
    writer.writeNumberField("totalDurationActual", totalDurationActual)
    return totalDurationActual
  }

  protected open fun writeProjectName(writer: JsonGenerator, projectName: String) {
    writer.writeStringField("project", projectName)
  }

  protected open fun writeExtraData(writer: JsonGenerator) {
  }

  protected open fun writeAppInfo(writer: JsonGenerator) {
  }

  private fun writeServiceEvents(writer: JsonGenerator, serviceActivities: Map<String, MutableList<ActivityImpl>>, startTime: Long) {
    val comparator = Comparator(::compareTime)
    for (name in serviceActivities.keys.sorted()) {
      val list = serviceActivities.getValue(name).sortedWith(comparator)
      val ownDurations = computeOwnTime(list, threadNameManager)
      writeActivities(list, startTime, writer, name, ownDurations, 0, TimeUnit.MICROSECONDS)
    }
  }

  fun toByteBuffer(): ByteBuffer {
    return stringWriter.toByteBuffer(logPrefix.length)
  }


  private fun writeParallelActivities(startTime: Long, writer: JsonGenerator) {
    // sorted to get predictable JSON
    for (name in activities.keys.sorted()) {
      val list = activities.getValue(name)
      StartUpPerformanceReporter.sortItems(list)

      val measureThreshold = if (name == ActivityCategory.APP_INIT.jsonName || name == ActivityCategory.REOPENING_EDITOR.jsonName) -1 else StartUpMeasurer.MEASURE_THRESHOLD
      val ownDurations = Object2LongOpenHashMap<ActivityImpl>()
      ownDurations.defaultReturnValue(-1)
      writeActivities(list, startTime, writer, activityNameToJsonFieldName(name), ownDurations, measureThreshold = measureThreshold, timeUnit = TimeUnit.MILLISECONDS)
    }
  }

  private fun writeActivities(activities: List<ActivityImpl>,
                              startTime: Long, writer: JsonGenerator,
                              fieldName: String,
                              ownDurations: Object2LongMap<ActivityImpl>,
                              measureThreshold: Long,
                              timeUnit: TimeUnit) {
    if (activities.isEmpty()) {
      return
    }

    writer.array(fieldName) {
      var skippedDuration = 0L
      for (item in activities) {
        val ownDuration = ownDurations.getLong(item)
        val ownOrTotalDuration = if (ownDuration == -1L) item.end - item.start else ownDuration

        beforeActivityWrite(item, ownOrTotalDuration, fieldName)

        if (ownOrTotalDuration <= measureThreshold) {
          skippedDuration += ownOrTotalDuration
          continue
        }

        writer.obj {
          writer.writeStringField("n", compactName(item.name))
          writer.writeNumberField("s", timeUnit.convert(item.start - startTime, TimeUnit.NANOSECONDS))
          writer.writeNumberField("d", timeUnit.convert(item.end - item.start, TimeUnit.NANOSECONDS))
          if (ownDuration != -1L) {
            writer.writeNumberField("od", timeUnit.convert(ownDuration, TimeUnit.NANOSECONDS))
          }
          // Do not write end to reduce size of report. `end` can be computed using `start + duration`
          writer.writeStringField("t", threadNameManager.getThreadName(item))
          if (item.pluginId != null) {
            writer.writeStringField("p", item.pluginId)
          }
        }
      }

      if (skippedDuration > 0) {
        writer.obj {
          writer.writeStringField("n", "Other")
          writer.writeNumberField("d", timeUnit.convert(skippedDuration, TimeUnit.NANOSECONDS))
          writer.writeNumberField("s", timeUnit.convert(activities.last().start - startTime, TimeUnit.NANOSECONDS))
        }
      }
    }
  }

  protected open fun beforeActivityWrite(item: ActivityImpl, ownOrTotalDuration: Long, fieldName: String) {
  }

  protected open fun writeItemTimeInfo(item: ActivityImpl, duration: Long, offset: Long, writer: JsonGenerator) {
    writer.writeNumberField("duration", TimeUnit.NANOSECONDS.toMillis(duration))
    writer.writeNumberField("start", TimeUnit.NANOSECONDS.toMillis(item.start - offset))
    writer.writeNumberField("end", TimeUnit.NANOSECONDS.toMillis(item.end - offset))
    writer.writeStringField("thread", threadNameManager.getThreadName(item))
    if (item.pluginId != null) {
      writer.writeStringField("p", item.pluginId)
    }
  }
}

private fun activityNameToJsonFieldName(name: String): String {
  return when (name.last()) {
    'y' -> name.substring(0, name.length - 1) + "ies"
    's' -> name
    else -> name.substring(0) + 's'
  }
}

private val packageNameReplacements = listOf("com.intellij." to "c.i.", "org.jetbrains." to "o.j.")

private fun compactName(name: String): String {
  for (replacement in packageNameReplacements) {
    if (name.startsWith(replacement.first)) {
      return "${replacement.second}${name.substring(replacement.first.length)}"
    }
  }
  return name
}

class ExposingCharArrayWriter : CharArrayWriter(8192) {
  fun toByteBuffer(offset: Int): ByteBuffer {
    return Charsets.UTF_8.encode(CharBuffer.wrap(buf, offset, count - offset))
  }
}