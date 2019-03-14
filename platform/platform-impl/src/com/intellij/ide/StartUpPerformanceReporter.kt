// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.google.gson.stream.JsonWriter
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpMeasurer.Item
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import gnu.trove.THashMap
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

private val LOG = Logger.getInstance(StartUpMeasurer::class.java)

class StartUpPerformanceReporter : StartupActivity, DumbAware {
  private val activationCount = AtomicInteger()
  // questions like "what if we have several projects to open? what if no projects at all?" are out of scope for now
  private val isLastEdtOptionTopHitProviderFinished = AtomicBoolean()

  private var startUpEnd = AtomicLong(-1)

  var lastReport: ByteArray? = null
    private set

  companion object {
    // need to be exposed for tests, but I don't want to expose as top-level function, so, as companion object
    fun sortItems(items: MutableList<Item>) {
      items.sortWith(Comparator { o1, o2 ->
        if (o1 == o2.parent) {
          return@Comparator -1
        }
        else if (o2 == o1.parent) {
          return@Comparator 1
        }

        when {
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
    val items = mutableListOf<Item>()
    val activities = THashMap<String, MutableList<Item>>()

    StartUpMeasurer.processAndClear(Consumer { item ->
      fun addActivity(name: String) {
        activities.getOrPut(name) { mutableListOf() }.add(item)
      }

      when {
        item.name.first() == '_' -> addActivity(item.name)
        item.level != null -> addActivity("${item.level!!.jsonFieldNamePrefix}${item.name.capitalize()}")
        else -> {
          when (val parallelActivity = item.parallelActivity) {
            null -> items.add(item)
            else -> addActivity(parallelActivity.jsonName)
          }
        }
      }
    })

    if (items.isEmpty() || (ApplicationManager.getApplication().isUnitTestMode && activationNumber > 2)) {
      return
    }

    sortItems(items)

    val stringWriter = StringWriter()
    val logPrefix = "=== Start: StartUp Measurement ===\n"
    stringWriter.write(logPrefix)
    val writer = JsonWriter(stringWriter)
    writer.setIndent("  ")
    writer.beginObject()

    writer.name("version").value("4")
    writeServiceStats(writer)

    val startTime = if (activationNumber == 0) StartUpMeasurer.getStartTime() else items.first().start

    writer.name("items")
    writer.beginArray()
    var totalDuration = if (activationNumber == 0) writeUnknown(writer, startTime, items.first().start, startTime) else 0
    for ((index, item) in items.withIndex()) {
      writer.beginObject()
      writer.name("name").value(item.name)
      if (item.description != null) {
        writer.name("description").value(item.description)
      }

      val duration = item.end - item.start
      if (!isSubItem(item, index, items)) {
        totalDuration += duration
      }

      writeItemTimeInfo(item, duration, startTime, writer)
      writer.endObject()
    }
    totalDuration += writeUnknown(writer, items.last().end, end, startTime)
    writer.endArray()

    writeActivities(activities, startTime, writer)

    writer.name("totalDurationComputed").value(TimeUnit.NANOSECONDS.toMillis(totalDuration))
    writer.name("totalDurationActual").value(TimeUnit.NANOSECONDS.toMillis(end - startTime))

    writer.endObject()
    writer.flush()

    lastReport = stringWriter.buffer.substring(logPrefix.length).toByteArray()

    stringWriter.write("\n=== Stop: StartUp Measurement ===")
    var string = stringWriter.toString()
    // to make output more compact (quite a lot slow components) - should we write own JSON encoder? well, for now potentially slow RegExp is ok
    string = string.replace(Regex(",\\s+(\"start\"|\"end\"|\\{)"), ", $1")
    LOG.info(string)
  }

  private fun writeActivities(activities: THashMap<String, MutableList<Item>>, startTime: Long, writer: JsonWriter) {
    // sorted to get predictable JSON
    for (name in activities.keys.sorted()) {
      val list = activities.get(name)!!
      sortItems(list)
      writeActivities(list, startTime, writer, activityNameToJsonFieldName(name))
    }
  }
}

private fun writeServiceStats(writer: JsonWriter) {
  class StatItem(val name: String) {
    var app = 0
    var project = 0
    var module = 0
  }

  // components can be inferred from data, but to verify that items reported correctly (and because for items threshold is applied (not all are reported))
  val component = StatItem("component")
  val service = StatItem("service")

  val plugins = PluginManagerCore.getLoadedPlugins(null)
  for (plugin in plugins) {
    service.app += (plugin as IdeaPluginDescriptorImpl).appServices.size
    service.project += plugin.projectServices.size
    service.module += plugin.moduleServices.size

    component.app += plugin.appComponents.size
    component.project += plugin.projectComponents.size
    component.module += plugin.moduleComponents.size
  }

  writer.name("stats")
  writer.beginObject()

  writer.name("plugin").value(plugins.size)

  for (statItem in listOf(component, service)) {
    writer.name(statItem.name)
    writer.beginObject()
    writer.name("app").value(statItem.app)
    writer.name("project").value(statItem.project)
    writer.name("module").value(statItem.module)
    writer.endObject()
  }

  writer.endObject()
}

private fun activityNameToJsonFieldName(name: String): String {
  val firstIndex = if (name.startsWith('_')) 1 else 0
  return when {
    name.last() == 'y' -> name.substring(firstIndex, name.length - 1) + "ies"
    else -> name.substring(firstIndex) + 's'
  }
}

private fun writeActivities(slowComponents: List<Item>, offset: Long, writer: JsonWriter, fieldName: String) {
  if (slowComponents.isEmpty()) {
    return
  }

  // actually here not all components, but only slow (>10ms - as it was before)
  writer.name(fieldName)
  writer.beginArray()

  for (item in slowComponents) {
    writer.beginObject()
    writer.name("name").value(item.description ?: item.name)
    writeItemTimeInfo(item, item.end - item.start, offset, writer)
    writer.endObject()
  }

  writer.endArray()
}

private fun writeItemTimeInfo(item: Item, duration: Long, offset: Long, writer: JsonWriter) {
  writer.name("duration").value(TimeUnit.NANOSECONDS.toMillis(duration))
  writer.name("start").value(TimeUnit.NANOSECONDS.toMillis(item.start - offset))
  writer.name("end").value(TimeUnit.NANOSECONDS.toMillis(item.end - offset))
}

private fun isSubItem(item: Item, itemIndex: Int, list: List<Item>): Boolean {
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

private fun writeUnknown(writer: JsonWriter, start: Long, end: Long, offset: Long): Long {
  val duration = end - start
  val durationInMs = TimeUnit.NANOSECONDS.toMillis(duration)
  if (durationInMs <= 1) {
    return 0
  }

  writer.beginObject()
  writer.name("name").value("unknown")
  writer.name("duration").value(durationInMs)
  writer.name("start").value(TimeUnit.NANOSECONDS.toMillis(start - offset))
  writer.name("end").value(TimeUnit.NANOSECONDS.toMillis(end - offset))
  writer.endObject()
  return duration
}