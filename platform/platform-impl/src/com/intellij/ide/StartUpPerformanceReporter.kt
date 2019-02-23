// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.google.gson.stream.JsonWriter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.StartUpMeasurer
import com.intellij.util.StartUpMeasurer.Item
import gnu.trove.THashMap
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

class StartUpPerformanceReporter : StartupActivity, DumbAware {
  private val activationCount = AtomicInteger()
  // questions like "what if we have several projects to open? what if no projects at all?" are out of scope for now
  private val isLastEdtOptionTopHitProviderFinished = AtomicBoolean()

  @Volatile
  private var end: Long = -1

  var lastReport: String? = null
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
    this.end = end
    val activationNumber = activationCount.getAndIncrement()
    if (isLastEdtOptionTopHitProviderFinished.get()) {
      // even if this activity executed in a pooled thread, better if it will not affect start-up in any way
      ApplicationManager.getApplication().executeOnPooledThread {
        logStats(end, activationNumber)
      }
    }
  }

  fun lastEdtOptionTopHitProviderFinished() {
    if (!isLastEdtOptionTopHitProviderFinished.compareAndSet(false, true)) {
      return
    }

    val end = end
    if (end != -1L) {
      ApplicationManager.getApplication().executeOnPooledThread {
        logStats(end, 0)
      }
    }
  }

  @Synchronized
  private fun logStats(end: Long, activationNumber: Int) {
    val log = Logger.getInstance(StartUpMeasurer::class.java)

    val activityDescriptors = listOf(ActivityDescriptor(StartUpMeasurer.Activities.COMPONENT_INITIALIZED_INTERNAL_NAME, "components"),
                                     ActivityDescriptor(StartUpMeasurer.Activities.PRELOAD_ACTIVITY_FINISHED, "preloadActivities"),
                                     ActivityDescriptor(StartUpMeasurer.Activities.OPTIONS_TOP_HIT_PROVIDER, "optionsTopHitProvidera"))

    val items = mutableListOf<Item>()
    val activities = THashMap<String, MutableList<Item>>()
    StartUpMeasurer.processAndClear(Consumer { item ->
      if (activityDescriptors.any { it.itemName === item.name }) {
        activities.getOrPut(item.name) { mutableListOf() }.add(item)
      }
      else {
        items.add(item)
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

    writer.name("version").value("1")

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

    for (activityDescriptor in activityDescriptors) {
      val list = activities.get(activityDescriptor.itemName) ?: continue
      sortItems(list)
      writeActivities(list, startTime, writer, activityDescriptor.jsonFieldName)
    }

    writer.name("totalDurationComputed").value(TimeUnit.NANOSECONDS.toMillis(totalDuration))
    writer.name("totalDurationActual").value(TimeUnit.NANOSECONDS.toMillis(end - startTime))

    writer.endObject()
    writer.flush()

    lastReport = stringWriter.buffer.substring(logPrefix.length)

    stringWriter.write("\n=== Stop: StartUp Measurement ===")
    var string = stringWriter.toString()
    // to make output more compact (quite a lot slow components) - should we write own JSON encoder? well, for now potentially slow RegExp is ok
    string = string.replace(Regex(",\\s+(\"start\"|\"end\"|\\{)"), ", $1")
    log.info(string)
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
      writer.name("name").value(item.description)
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
}

private data class ActivityDescriptor(val itemName: String, val jsonFieldName: String)