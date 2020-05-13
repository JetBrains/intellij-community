// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.containers.ObjectIntHashMap
import com.intellij.util.containers.ObjectLongHashMap
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.CharArrayWriter
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
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

  val publicStatMetrics = ObjectIntHashMap<String>()

  fun write(timeOffset: Long, items: List<ActivityImpl>, serviceActivities: Map<String, MutableList<ActivityImpl>>, instantEvents: List<ActivityImpl>, end: Long, projectName: String) {
    stringWriter.write(logPrefix)

    val writer = JsonFactory().createGenerator(stringWriter)
    writer.prettyPrinter = MyJsonPrettyPrinter()
    writer.use {
      writer.obj {
        writer.writeStringField("version", StartUpPerformanceReporter.VERSION)
        val appInfo = ApplicationInfo.getInstance()
        writer.writeStringField("build", appInfo.build.asStringWithoutProductCode())
        writer.writeStringField("buildDate", ZonedDateTime.ofInstant(appInfo.buildDate.toInstant(), ZoneId.systemDefault()).format(DateTimeFormatter.RFC_1123_DATE_TIME))
        writer.writeStringField("productCode", appInfo.build.productCode)
        writer.writeStringField("generated", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
        writer.writeStringField("os", SystemInfo.getOsNameAndVersion())
        writer.writeStringField("runtime", SystemInfo.JAVA_VENDOR + " " + SystemInfo.JAVA_VERSION + " " + SystemInfo.JAVA_RUNTIME_VERSION)

        // see CDSManager from platform-impl
        @Suppress("SpellCheckingInspection")
        if (ManagementFactory.getRuntimeMXBean().inputArguments.any { it == "-Xshare:auto" || it == "-Xshare:on" }) {
          writer.writeBooleanField("cds", true)
        }

        writer.writeStringField("project", safeHashValue(projectName))

        writeServiceStats(writer)
        writeIcons(writer)

        writer.array("traceEvents") {
          val traceEventFormatWriter = TraceEventFormatWriter(timeOffset, instantEvents, threadNameManager)
          traceEventFormatWriter.writeInstantEvents(writer)
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

              if (item.name == "bootstrap" || item.name == "app initialization") {
                publicStatMetrics.put(item.name, TimeUnit.NANOSECONDS.toMillis(duration).toInt())
              }

              writeItemTimeInfo(item, duration, timeOffset, writer)
            }
          }
        }

        totalDuration += end - items.last().end

        writeParallelActivities(timeOffset, writer)

        writer.writeNumberField("totalDurationComputed", TimeUnit.NANOSECONDS.toMillis(totalDuration))
        val totalDurationActual = TimeUnit.NANOSECONDS.toMillis(end - timeOffset)
        writer.writeNumberField("totalDurationActual", totalDurationActual)

        publicStatMetrics.put("totalDuration", totalDurationActual.toInt())
      }
    }
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
      writeActivities(list, startTime, writer, activityNameToJsonFieldName(name), ObjectLongHashMap(), measureThreshold = measureThreshold, timeUnit = TimeUnit.MILLISECONDS)
    }
  }

  private fun writeActivities(activities: List<ActivityImpl>,
                              startTime: Long, writer: JsonGenerator,
                              fieldName: String,
                              ownDurations: ObjectLongHashMap<ActivityImpl>,
                              measureThreshold: Long,
                              timeUnit: TimeUnit) {
    if (activities.isEmpty()) {
      return
    }

    writer.array(fieldName) {
      var skippedDuration = 0L
      for (item in activities) {
        val ownDuration = ownDurations.get(item)
        val ownOrTotalDuration = if (ownDuration == -1L) item.end - item.start else ownDuration
        item.pluginId?.let {
          StartUpMeasurer.doAddPluginCost(it, item.category?.name ?: "unknown", ownOrTotalDuration, pluginCostMap)
        }

        val name = item.name
        if (fieldName == "prepareAppInitActivities" && name == "splash initialization") {
          publicStatMetrics.put("splash", TimeUnit.NANOSECONDS.toMillis(ownOrTotalDuration).toInt())
        }

        if (ownOrTotalDuration <= measureThreshold) {
          skippedDuration += ownOrTotalDuration
          continue
        }

        writer.obj {
          writer.writeStringField("n", compactName(name))
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

  private fun writeItemTimeInfo(item: ActivityImpl, duration: Long, offset: Long, writer: JsonGenerator) {
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

private fun safeHashValue(value: String): String {
  val generator = Argon2BytesGenerator()
  generator.init(Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).build())
  // 160 bit is enough for uniqueness
  val result = ByteArray(20)
  generator.generateBytes(value.toByteArray(), result, 0, result.size)
  return Base64.getEncoder().withoutPadding().encodeToString(result)
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
