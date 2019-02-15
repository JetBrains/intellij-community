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

internal class StartUpPerformanceReporter : StartupActivity, DumbAware {
  override fun runActivity(project: Project) {
    val end = System.currentTimeMillis()
    // even if this activity executed in a pooled thread, better if it will not affect start-up in any way,
    ApplicationManager.getApplication().executeOnPooledThread {
      logStats(end)
    }
  }

  private fun logStats(end: Long) {
    val log = Logger.getInstance(StartUpMeasurer::class.java)

    val items: MutableList<StartUpMeasurer.Item> = StartUpMeasurer.getAndClear()
    if (items.isEmpty()) {
      return
    }

    items.sortWith(Comparator { o1, o2 -> (o1.start - o2.start).toInt() })

    val stringWriter = StringWriter()
    val writer = JsonWriter(stringWriter)
    writer.setIndent("  ")

    writer.beginObject()

    var totalDuration = 0L

    writer.name("items")
    writer.beginArray()

    val start = StartUpMeasurer.getStart()

    totalDuration += writeUnknown(writer, start, items.get(0).start)

    for ((index, item) in items.withIndex()) {
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

      writer.name("duration").value(duration)
      writer.name("start").value(item.start)
      writer.name("end").value(item.end)
      writer.endObject()
    }

    totalDuration += writeUnknown(writer, items.last().end, end)

    writer.endArray()

    writer.name("totalDurationComputed").value(totalDuration)
    writer.name("totalDurationActual").value(end - items.first().start)

    writer.endObject()
    log.info(stringWriter.toString())
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