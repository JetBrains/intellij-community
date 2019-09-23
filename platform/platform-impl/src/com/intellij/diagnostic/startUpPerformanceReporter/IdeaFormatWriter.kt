// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.containers.ObjectLongHashMap
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import java.io.CharArrayWriter
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private class ExposingCharArrayWriter : CharArrayWriter(8192) {
  fun toByteBuffer(offset: Int): ByteBuffer {
    return Charsets.UTF_8.encode(CharBuffer.wrap(buf, offset, count - offset))
  }
}

internal class IdeaFormatWriter(private val activities: Map<String, MutableList<ActivityImpl>>,
                                private val pluginCostMap: MutableMap<String, ObjectLongHashMap<String>>,
                                private val threadNameManager: ThreadNameManager) {
  private val logPrefix = "=== Start: StartUp Measurement ===\n"

  private val stringWriter = ExposingCharArrayWriter()

  fun write(timeOffset: Long, items: List<ActivityImpl>, services: List<ActivityImpl>, instantEvents: List<ActivityImpl>, end: Long) {
    stringWriter.write(logPrefix)

    val writer = JsonFactory().createGenerator(stringWriter)
    writer.prettyPrinter = MyJsonPrettyPrinter()
    writer.use {
      writer.obj {
        writer.writeStringField("version", StartUpPerformanceReporter.VERSION)
        writer.writeStringField("build", ApplicationInfo.getInstance().build.asStringWithoutProductCode())
        writer.writeStringField("productCode", ApplicationInfo.getInstance().build.productCode)
        writer.writeStringField("generated", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
        writer.writeStringField("os", SystemInfo.getOsNameAndVersion())
        writer.writeStringField("runtime", SystemInfo.JAVA_VENDOR + " " + SystemInfo.JAVA_VERSION + " " + SystemInfo.JAVA_RUNTIME_VERSION)
        writeServiceStats(writer)
        writeIcons(writer)

        writer.array("traceEvents") {
          val traceEventFormatWriter = TraceEventFormatWriter(timeOffset, instantEvents, threadNameManager)
          traceEventFormatWriter.writeInstantEvents(writer)
          traceEventFormatWriter.writeServiceEvents(writer, services, pluginCostMap)
        }

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

        writer.writeNumberField("totalDurationComputed", TimeUnit.NANOSECONDS.toMillis(totalDuration))
        writer.writeNumberField("totalDurationActual", TimeUnit.NANOSECONDS.toMillis(end - timeOffset))
      }
    }
  }

  fun toByteBuffer(): ByteBuffer {
    return stringWriter.toByteBuffer(logPrefix.length)
  }

  fun writeToLog(log: Logger) {
    stringWriter.write("\n=== Stop: StartUp Measurement ===")
    log.info(stringWriter.toString())
  }

  private fun writeParallelActivities(startTime: Long, writer: JsonGenerator) {
    // sorted to get predictable JSON
    for (name in activities.keys.sorted()) {
      val list = activities.getValue(name)
      StartUpPerformanceReporter.sortItems(list)

      val measureThreshold = if (name == ActivityCategory.APP_INIT.jsonName || name == ActivityCategory.REOPENING_EDITOR.jsonName) -1 else StartUpMeasurer.MEASURE_THRESHOLD
      writeActivities(list, startTime, writer, activityNameToJsonFieldName(name), ObjectLongHashMap(), measureThreshold = measureThreshold)
    }
  }

  private fun writeActivities(activities: List<ActivityImpl>,
                              offset: Long, writer: JsonGenerator,
                              fieldName: String,
                              ownDurations: ObjectLongHashMap<ActivityImpl>,
                              measureThreshold: Long = StartUpMeasurer.MEASURE_THRESHOLD) {
    if (activities.isEmpty()) {
      return
    }

    writer.array(fieldName) {
      var skippedDuration = 0L
      for (item in activities) {
        val computedOwnDuration = ownDurations.get(item)
        val duration = if (computedOwnDuration == -1L) item.end - item.start else computedOwnDuration

        item.pluginId?.let {
          StartUpMeasurer.doAddPluginCost(it, item.category?.name ?: "unknown", duration, pluginCostMap)
        }

        if (duration <= measureThreshold) {
          skippedDuration += duration
          continue
        }

        writer.obj {
          writer.writeStringField("name", item.name)
          writeItemTimeInfo(item, duration, offset, writer)
        }
      }

      if (skippedDuration > 0) {
        writer.obj {
          writer.writeStringField("name", "Other")
          writer.writeNumberField("duration", TimeUnit.NANOSECONDS.toMillis(skippedDuration))
          writer.writeNumberField("start", TimeUnit.NANOSECONDS.toMillis(activities.last().start - offset))
          writer.writeNumberField("end", TimeUnit.NANOSECONDS.toMillis(activities.last().end - offset))
        }
      }
    }
  }

  private fun writeItemTimeInfo(item: ActivityImpl, duration: Long, offset: Long, writer: JsonGenerator) {
    writer.writeNumberField("duration", TimeUnit.NANOSECONDS.toMillis(duration))
    writer.writeNumberField("start", TimeUnit.NANOSECONDS.toMillis(item.start - offset))
    writer.writeNumberField("end", TimeUnit.NANOSECONDS.toMillis(item.end - offset))
    writer.writeStringField("thread", threadNameManager.getThreadName(item))
    if (item.pluginId != null) {
      writer.writeStringField("plugin", item.pluginId)
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

private fun writeIcons(writer: JsonGenerator) {
  writer.array("icons") {
    for (stat in IconLoadMeasurer.getStats()) {
      writer.obj {
        writer.writeStringField("name", stat.type)
        writer.writeNumberField("count", stat.counter)
        writer.writeNumberField("time", TimeUnit.NANOSECONDS.toMillis(stat.totalTime))
      }
    }
  }
}