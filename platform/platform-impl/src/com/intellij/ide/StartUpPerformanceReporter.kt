// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.google.gson.stream.JsonWriter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.StartUpMeasurer
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicInteger

internal class StartUpPerformanceReporter : StartupActivity, DumbAware {
  private val activationCount = AtomicInteger()

  override fun runActivity(project: Project) {
    val end = System.currentTimeMillis()
    val activationNumber = activationCount.incrementAndGet()
    // even if this activity executed in a pooled thread, better if it will not affect start-up in any way,
    ApplicationManager.getApplication().executeOnPooledThread {
      logStats(end, activationNumber)
    }
  }

  private fun logStats(end: Long, activationNumber: Int) {
    val log = Logger.getInstance(StartUpMeasurer::class.java)

    val items: MutableList<StartUpMeasurer.Item> = StartUpMeasurer.getAndClear()
    if (items.isEmpty() || (ApplicationManager.getApplication().isUnitTestMode && activationNumber > 2)) {
      return
    }


    // project components initialization must be first
//    {
//          "name": "project components registration",
//          "duration": 398,
//          "start": 1550307664977,
//          "end": 1550307665375
//        },
//        {
//          "name": "project components initialization",
//          "description": "component count: 212",
//          "duration": 1302,
//          "start": 1550307664977,
//          "end": 1550307666279
//        },
//        {
//          "name": "project components creation",
//          "isSubItem": true,
//          "duration": 904,
//          "start": 1550307665375,
//          "end": 1550307666279
//        },
    items.sortWith(Comparator { o1, o2 ->
      val diff = (o1.start - o2.start).toInt()
      if (diff != 0) {
        return@Comparator diff
      }

      (o2.end - o1.end).toInt()
    })

    val stringWriter = StringWriter()
    val writer = JsonWriter(stringWriter)
    writer.setIndent("  ")
    writer.beginObject()

    writer.name("items")
    writer.beginArray()
    var totalDuration = if (activationNumber == 0) writeUnknown(writer, StartUpMeasurer.getStartTime(), items.get(0).start) else 0
    val slowComponents = mutableListOf<StartUpMeasurer.Item>()
    for ((index, item) in items.withIndex()) {
      if (item.name === StartUpMeasurer.COMPONENT_INITIALIZED_INTERNAL_NAME) {
        slowComponents.add(item)
        continue
      }

      writer.beginObject()
      writer.name("name").value(item.name)
      if (item.description != null) {
        writer.name("description").value(item.description)
      }

      val duration = item.end - item.start

      if (isSubItem(item, index, items)) {
        // for debug purposes (check that isSubItem computed correctly)
        writer.name("isSubItem").value(true)
      }
      else {
        totalDuration += duration
      }

      writeItemTimeInfo(item, duration, writer)
      writer.endObject()
    }
    totalDuration += writeUnknown(writer, items.last().end, end)
    writer.endArray()

    writeComponents(slowComponents, writer)

    writer.name("totalDurationComputed").value(totalDuration)
    writer.name("totalDurationActual").value(end - items.first().start)

    writer.endObject()
    var string = stringWriter.toString()
    // to make output more compact (quite a lot slow components) - should we write own JSON encoder? well, for now potentially slow RegExp is ok
    string = string.replace(Regex(",\\s+(\"start\"|\"end\"|\\{)"), ", $1")
    log.info(string)
  }

  private fun writeComponents(slowComponents: List<StartUpMeasurer.Item>, writer: JsonWriter) {
    if (slowComponents.isEmpty()) {
      return
    }

    // actually here not all components, but only slow (>10ms - as it was before)
    writer.name("components")
    writer.beginArray()

    for (item in slowComponents) {
      writer.beginObject()
      writer.name("name").value(item.description)
      writeItemTimeInfo(item, item.end - item.start, writer)
      writer.endObject()
    }

    writer.endArray()
  }

  private fun writeItemTimeInfo(item: StartUpMeasurer.Item, duration: Long, writer: JsonWriter) {
    writer.name("duration").value(duration)
    writer.name("start").value(item.start)
    writer.name("end").value(item.end)
  }

  private fun isSubItem(item: StartUpMeasurer.Item, itemIndex: Int, list: List<StartUpMeasurer.Item>): Boolean {
    var index = itemIndex
    while (true) {
      val prevItem = list.getOrNull(--index) ?: return false
      // items are sorted, no need to check start or next items
      if (prevItem.end >= item.end) {
        return true
      }
    }
  }

  private fun writeUnknown(writer: JsonWriter, start: Long, end: Long): Long {
    val duration = end - start
    if (duration <= 1) {
      return 0
    }

    writer.beginObject()
    writer.name("name").value("unknown")
    writer.name("duration").value(duration)
    writer.name("start").value(start)
    writer.name("end").value(end)
    writer.endObject()
    return duration
  }
}