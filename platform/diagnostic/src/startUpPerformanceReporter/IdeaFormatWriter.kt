// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

internal abstract class IdeaFormatWriter(private val activities: Map<String, MutableList<ActivityImpl>>,
                                         private val threadNameManager: ThreadNameManager,
                                         private val version: String) {
  private val logPrefix: String = "=== Start: StartUp Measurement ===\n"
  protected val stringWriter: ExposingCharArrayWriter = ExposingCharArrayWriter()

  fun write(timeOffset: Long,
            serviceActivities: Map<String, MutableList<ActivityImpl>>,
            instantEvents: List<ActivityImpl>,
            end: Long,
            projectName: String) {
    stringWriter.write(logPrefix)

    val writer = JsonFactory().createGenerator(stringWriter)
    writer.prettyPrinter = MyJsonPrettyPrinter()
    writer.use {
      writer.obj {
        writer.writeStringField("version", version)
        writeAppInfo(writer)
        writer.writeStringField("generated", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
        writer.writeStringField("os", SystemInfo.getOsNameAndVersion())
        writer.writeStringField("runtime", SystemInfo.JAVA_VENDOR + " " + SystemInfo.JAVA_VERSION + " " + SystemInfo.JAVA_RUNTIME_VERSION)

        writeProjectName(writer, projectName)
        writeExtraData(writer)

        writeParallelActivities(timeOffset, writer)
        writer.array("traceEvents") {
          writeInstantEvents(writer, instantEvents, timeOffset, threadNameManager)
        }
        writeServiceEvents(writer, serviceActivities, timeOffset)
        writeTotalDuration(writer, end, timeOffset)
      }
    }
  }

  protected open fun writeTotalDuration(writer: JsonGenerator, end: Long, timeOffset: Long): Long {
    val totalDurationActual = TimeUnit.NANOSECONDS.toMillis(end - timeOffset)
    writer.writeNumberField("totalDuration", totalDurationActual)
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
      writeActivities(list, startTime, writer, name, ownDurations, 0, DurationUnit.MICROSECONDS)
    }
  }

  fun toByteBuffer(): ByteBuffer {
    return stringWriter.toByteBuffer(logPrefix.length)
  }

  private fun writeParallelActivities(startTime: Long, writer: JsonGenerator) {
    for ((name, list ) in activities) {
      StartUpPerformanceReporter.sortItems(list)

      val measureThreshold = if (name == ActivityCategory.DEFAULT.jsonName || name == ActivityCategory.REOPENING_EDITOR.jsonName) {
        -1
      }
      else {
        StartUpMeasurer.MEASURE_THRESHOLD
      }
      val ownDurations = Object2LongOpenHashMap<ActivityImpl>()
      ownDurations.defaultReturnValue(-1)
      writeActivities(activities = list,
                      startTime = startTime,
                      writer = writer,
                      fieldName = activityNameToJsonFieldName(name),
                      ownDurations = ownDurations,
                      measureThreshold = measureThreshold,
                      timeUnit = DurationUnit.MILLISECONDS)
    }
  }

  private fun writeActivities(activities: List<ActivityImpl>,
                              startTime: Long, writer: JsonGenerator,
                              fieldName: String,
                              ownDurations: Object2LongMap<ActivityImpl>,
                              measureThreshold: Long,
                              timeUnit: DurationUnit) {
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

        val convertedDuration = (item.end - item.start).nanoseconds.toLong(timeUnit)
        if (convertedDuration == 0L && (item.name.endsWith(": completing") || item.name.endsWith(": scheduled"))) {
          continue
        }

        writer.obj {
          writer.writeStringField("n", compactName(item.name))
          writer.writeNumberField("s", (item.start - startTime).nanoseconds.toLong(timeUnit))
          writer.writeNumberField("d", convertedDuration)
          if (ownDuration != -1L) {
            writer.writeNumberField("od", ownDuration.nanoseconds.toLong(timeUnit))
          }
          // Do not write an end to reduce the size of the report. An `end` can be computed using `start + duration`
          writer.writeStringField("t", threadNameManager.getThreadName(item))
          if (item.pluginId != null) {
            writer.writeStringField("p", item.pluginId)
          }
        }
      }

      if (skippedDuration > 0) {
        writer.obj {
          writer.writeStringField("n", "Other")
          writer.writeNumberField("d", skippedDuration.nanoseconds.toLong(timeUnit))
          writer.writeNumberField("s", (activities.last().start - startTime).nanoseconds.toLong(timeUnit))
        }
      }
    }
  }

  protected open fun beforeActivityWrite(item: ActivityImpl, ownOrTotalDuration: Long, fieldName: String) {
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

private fun writeInstantEvents(writer: JsonGenerator,
                               instantEvents: List<ActivityImpl>,
                               timeOffset: Long,
                               threadNameManager: ThreadNameManager) {
  for (event in instantEvents) {
    writer.obj {
      writeCommonFields(event, writer, timeOffset, threadNameManager)
      writer.writeStringField("ph", "i")
      writer.writeStringField("s", "g")
    }
  }
}

private fun writeCommonFields(event: ActivityImpl, writer: JsonGenerator, timeOffset: Long, threadNameManager: ThreadNameManager) {
  writer.writeStringField("name", event.name)
  writer.writeNumberField("ts", TimeUnit.NANOSECONDS.toMicros(event.start - timeOffset))
  writer.writeNumberField("pid", 1)
  writer.writeStringField("tid", threadNameManager.getThreadName(event))

  event.category?.let {
    writer.writeStringField("cat", it.jsonName)
  }
}

class ExposingCharArrayWriter : CharArrayWriter(8192) {
  fun toByteBuffer(offset: Int): ByteBuffer {
    return Charsets.UTF_8.encode(CharBuffer.wrap(buf, offset, count - offset))
  }
}