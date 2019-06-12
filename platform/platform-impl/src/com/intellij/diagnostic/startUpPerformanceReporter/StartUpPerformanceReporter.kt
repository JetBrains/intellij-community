// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.ParallelActivity
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ObjectLongHashMap
import com.intellij.util.io.jackson.IntelliJPrettyPrinter
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import gnu.trove.THashMap
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import kotlin.Comparator

internal val LOG = logger<StartUpMeasurer>()

class StartUpPerformanceReporter : StartupActivity, DumbAware {
  private val activationCount = AtomicInteger()
  // questions like "what if we have several projects to open? what if no projects at all?" are out of scope for now
  private val isLastEdtOptionTopHitProviderFinished = AtomicBoolean()

  private var startUpEnd = AtomicLong(-1)

  var pluginCostMap: Map<String, ObjectLongHashMap<String>>? = null
    private set

  var lastReport: ByteArray? = null
    private set

  companion object {
    // need to be exposed for tests, but I don't want to expose as top-level function, so, as companion object
    fun sortItems(items: MutableList<ActivityImpl>) {
      items.sortWith(Comparator { o1, o2 ->
        if (o1 == o2.parent) {
          return@Comparator -1
        }
        else if (o2 == o1.parent) {
          return@Comparator 1
        }

        compareTime(o1, o2)
      })
    }
  }

  override fun runActivity(project: Project) {
    val end = System.nanoTime()
    val activationNumber = activationCount.getAndIncrement()
    if (activationNumber == 0) {
      LOG.assertTrue(startUpEnd.getAndSet(end) == -1L)
    }
    if (isLastEdtOptionTopHitProviderFinished.get()) {
      // even if this activity executed in a pooled thread, better if it will not affect start-up in any way
      ApplicationManager.getApplication().executeOnPooledThread {
        logStats(end, activationNumber)
      }
    }
  }

  fun lastEdtOptionTopHitProviderFinishedForProject() {
    if (!isLastEdtOptionTopHitProviderFinished.compareAndSet(false, true)) {
      return
    }

    val end = startUpEnd.get()
    if (end != -1L) {
      ApplicationManager.getApplication().executeOnPooledThread {
        logStats(end, 0)
      }
    }
  }

  @Synchronized
  private fun logStats(end: Long, activationNumber: Int) {
    val items = mutableListOf<ActivityImpl>()
    val activities = THashMap<String, MutableList<ActivityImpl>>()

    StartUpMeasurer.processAndClear(SystemProperties.getBooleanProperty("idea.collect.perf.after.first.project", false), Consumer { item ->
      val parallelActivity = item.parallelActivity
      if (parallelActivity == null) {
        items.add(item)
      }
      else {
        val level = item.level
        var name = parallelActivity.jsonName
        if (level != null) {
          name = "${level.jsonFieldNamePrefix}${name.capitalize()}"
        }
        activities.getOrPut(name) { mutableListOf() }.add(item)
      }
    })

    if (items.isEmpty()) {
      return
    }

    sortItems(items)

    val pluginCostMap = computePluginCostMap()

    val stringWriter = StringWriter()
    val logPrefix = "=== Start: StartUp Measurement ===\n"
    stringWriter.write(logPrefix)

    val writer = JsonFactory().createGenerator(stringWriter)
    writer.prettyPrinter = MyJsonPrettyPrinter()
    writer.use {
      writer.obj {
        writer.writeStringField("version", "7")
        writeServiceStats(writer)
        writeIcons(writer)

        var startTime = if (activationNumber == 0) StartUpMeasurer.getClassInitStartTime() else items.first().start
        for (item in items) {
          if (item.start < startTime) {
            startTime = item.start
          }
        }

        var totalDuration: Long = 0
        writer.array("items") {
          totalDuration = if (activationNumber == 0) writeUnknown(writer, startTime, items.first().start, startTime) else 0

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

              item.pluginId?.let {
                StartUpMeasurer.doAddPluginCost(it, item.parallelActivity?.name ?: "unknown", duration, pluginCostMap)
              }
              writeItemTimeInfo(item, duration, startTime, writer)
            }
          }
          totalDuration += writeUnknown(writer, items.last().end, end, startTime)
        }

        writeParallelActivities(activities, startTime, writer, pluginCostMap)

        writer.writeNumberField("totalDurationComputed", TimeUnit.NANOSECONDS.toMillis(totalDuration))
        writer.writeNumberField("totalDurationActual", TimeUnit.NANOSECONDS.toMillis(end - startTime))
      }
    }

    lastReport = stringWriter.buffer.substring(logPrefix.length).toByteArray()

    if (SystemProperties.getBooleanProperty("idea.log.perf.stats", true)) {
      stringWriter.write("\n=== Stop: StartUp Measurement ===")
      LOG.info(stringWriter.toString())
    }
  }

  private fun computePluginCostMap(): MutableMap<String, ObjectLongHashMap<String>> {
    var pluginCostMap: MutableMap<String, ObjectLongHashMap<String>>
    synchronized(StartUpMeasurer.pluginCostMap) {
      pluginCostMap = THashMap(StartUpMeasurer.pluginCostMap)
      StartUpMeasurer.pluginCostMap.clear()
    }
    this.pluginCostMap = pluginCostMap


    for (plugin in PluginManagerCore.getLoadedPlugins()) {
      val id = plugin.pluginId.idString
      val classLoader = (plugin as IdeaPluginDescriptorImpl).pluginClassLoader as? PluginClassLoader ?: continue
      val costPerPhaseMap = pluginCostMap.getOrPut(id) { ObjectLongHashMap() }
      costPerPhaseMap.put("classloading (EDT)", classLoader.edtTime)
      costPerPhaseMap.put("classloading (background)", classLoader.backgroundTime)
    }
    return pluginCostMap
  }
}

// to make output more compact (quite a lot slow components)
private class MyJsonPrettyPrinter : IntelliJPrettyPrinter() {
  private var objectLevel = 0

  override fun writeStartObject(g: JsonGenerator) {
    objectLevel++
    if (objectLevel > 1) {
      _objectIndenter = FixedSpaceIndenter.instance
    }
    super.writeStartObject(g)
  }

  override fun writeEndObject(g: JsonGenerator, nrOfEntries: Int) {
    super.writeEndObject(g, nrOfEntries)
    objectLevel--
    if (objectLevel <= 1) {
      _objectIndenter = UNIX_LINE_FEED_INSTANCE
    }
  }
}

private fun writeParallelActivities(activities: Map<String, MutableList<ActivityImpl>>, startTime: Long, writer: JsonGenerator, pluginCostMap: MutableMap<String, ObjectLongHashMap<String>>) {
  val ownDurations = ObjectLongHashMap<ActivityImpl>()

  // sorted to get predictable JSON
  for (name in activities.keys.sorted()) {
    ownDurations.clear()

    val list = activities.getValue(name)
    StartUpPerformanceReporter.sortItems(list)

    if (name.endsWith("Component")) {
      computeOwnTime(list, ownDurations)
    }

    val measureThreshold = if (name == ParallelActivity.PREPARE_APP_INIT.jsonName || name == ParallelActivity.REOPENING_EDITOR.jsonName) -1 else ParallelActivity.MEASURE_THRESHOLD
    writeActivities(list, startTime, writer, activityNameToJsonFieldName(name), ownDurations, pluginCostMap, measureThreshold = measureThreshold)
  }
}

private fun activityNameToJsonFieldName(name: String): String {
  return when {
    name.last() == 'y' -> name.substring(0, name.length - 1) + "ies"
    else -> name.substring(0) + 's'
  }
}

private fun writeActivities(activities: List<ActivityImpl>,
                            offset: Long, writer: JsonGenerator,
                            fieldName: String,
                            ownDurations: ObjectLongHashMap<ActivityImpl>,
                            pluginCostMap: MutableMap<String, ObjectLongHashMap<String>>,
                            measureThreshold: Long = ParallelActivity.MEASURE_THRESHOLD) {
  if (activities.isEmpty()) {
    return
  }

  writer.array(fieldName) {
    var skippedDuration = 0L
    for (item in activities) {
      val computedOwnDuration = ownDurations.get(item)
      val duration = if (computedOwnDuration == -1L) item.end - item.start else computedOwnDuration
      if (duration <= measureThreshold) {
        skippedDuration += duration
        continue
      }

      item.pluginId?.let {
        StartUpMeasurer.doAddPluginCost(it, item.parallelActivity?.name ?: "unknown", duration, pluginCostMap)
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
  writer.writeStringField("thread", normalizeThreadName(item.thread))
  if (item.pluginId != null) {
    writer.writeStringField("plugin", item.pluginId)
  }
}

private fun normalizeThreadName(name: String): String {
  return when {
    name.startsWith("AWT-EventQueue-") -> "edt"
    name.startsWith("Idea Main Thread") -> "idea main"
    name.startsWith("ApplicationImpl pooled thread ") -> name.replace("ApplicationImpl pooled thread ", "pooled ")
    else -> name
  }
}

private fun isSubItem(item: ActivityImpl, itemIndex: Int, list: List<ActivityImpl>): Boolean {
  if (item.parent != null) {
    return true
  }

  var index = itemIndex
  while (true) {
    val prevItem = list.getOrNull(--index) ?: return false
    // items are sorted, no need to check start or next items
    if (prevItem.end >= item.end) {
      return true
    }
  }
}

private fun writeUnknown(writer: JsonGenerator, start: Long, end: Long, offset: Long): Long {
  val duration = end - start
  val durationInMs = TimeUnit.NANOSECONDS.toMillis(duration)
  if (durationInMs <= 1) {
    return 0
  }

  writer.obj {
    writer.writeStringField("name", "unknown")
    writer.writeNumberField("duration", durationInMs)
    writer.writeNumberField("start", TimeUnit.NANOSECONDS.toMillis(start - offset))
    writer.writeNumberField("end", TimeUnit.NANOSECONDS.toMillis(end - offset))
  }
  return duration
}

private fun compareTime(o1: ActivityImpl, o2: ActivityImpl): Int {
  return when {
    o1.start > o2.start -> 1
    o1.start < o2.start -> -1
    else -> {
      when {
        o1.end > o2.end -> -1
        o1.end < o2.end -> 1
        else -> 0
      }
    }
  }
}

private fun writeIcons(writer: JsonGenerator) {
  fun writeStats(infoList: List<IconLoadMeasurer>) {
    writer.obj(infoList[0].type.name.toLowerCase()) {
      writer.writeNumberField("count", infoList[0].counter)
      writer.writeNumberField("loading", TimeUnit.NANOSECONDS.toMillis(infoList[0].totalTime.toLong()))
      writer.writeNumberField("decoding", TimeUnit.NANOSECONDS.toMillis(infoList[1].totalTime.toLong()))
    }
  }

  val map = linkedMapOf<String, MutableList<IconLoadMeasurer>>()
  for (stat in IconLoadMeasurer.getStats()) {
    map.getOrPut(stat.type.name.toLowerCase()) { mutableListOf() }.add(stat)
  }

  writer.obj("icons") {
    for (infoList in map.values) {
      writeStats(infoList)
    }
  }
}