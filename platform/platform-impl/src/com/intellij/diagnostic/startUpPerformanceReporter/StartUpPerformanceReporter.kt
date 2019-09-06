// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ObjectLongHashMap
import com.intellij.util.io.jackson.IntelliJPrettyPrinter
import com.intellij.util.io.outputStream
import com.intellij.util.io.write
import gnu.trove.THashMap
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import kotlin.Comparator

class StartUpPerformanceReporter : StartupActivity.DumbAware {
  private val activationCount = AtomicInteger()
  // questions like "what if we have several projects to open? what if no projects at all?" are out of scope for now
  private val isLastEdtOptionTopHitProviderFinished = AtomicBoolean()

  private var startUpEnd = AtomicLong(-1)

  var pluginCostMap: Map<String, ObjectLongHashMap<String>>? = null
    private set

  var lastReport: ByteBuffer? = null
    private set

  companion object {
    internal val LOG = logger<StartUpMeasurer>()

    internal fun sortItems(items: MutableList<ActivityImpl>) {
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

  fun lastOptionTopHitProviderFinishedForProject() {
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
    val instantEvents = mutableListOf<ActivityImpl>()
    val activities = THashMap<String, MutableList<ActivityImpl>>()

    val threadNameManager = ThreadNameManager()

    StartUpMeasurer.processAndClear(SystemProperties.getBooleanProperty("idea.collect.perf.after.first.project", false), Consumer { item ->
      // process it now to ensure that thread will have first name (because report writer can process events in any order)
      threadNameManager.getThreadName(item)

      if (item.end == -1L) {
        instantEvents.add(item)
      }
      else {
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
      }
    })

    if (items.isEmpty()) {
      return
    }

    sortItems(items)

    val pluginCostMap = computePluginCostMap()
    this.pluginCostMap = pluginCostMap

    val w = IdeaFormatWriter(activities, pluginCostMap, threadNameManager)
    val startTime = if (activationNumber == 0) StartUpMeasurer.getStartTime() else items.first().start
    for (item in items) {
      val pluginId = item.pluginId ?: continue
      StartUpMeasurer.doAddPluginCost(pluginId, item.parallelActivity?.name ?: "unknown", item.end - item.start, pluginCostMap)
    }

    w.write(startTime, items, instantEvents, end)

    val currentReport = w.toByteBuffer()
    lastReport = currentReport

    if (SystemProperties.getBooleanProperty("idea.log.perf.stats", ApplicationManager.getApplication().isInternal || ApplicationInfoEx.getInstanceEx().build.isSnapshot)) {
      w.writeToLog(LOG)
    }

    val perfFilePath = System.getProperty("idea.log.perf.stats.file")
    if (!perfFilePath.isNullOrBlank()) {
      LOG.info("StartUp Measurement report was written to: ${perfFilePath}")
      Paths.get(perfFilePath).write(currentReport)
    }

    val traceFilePath = System.getProperty("idea.log.perf.trace.file")
    if (!traceFilePath.isNullOrBlank()) {
      val traceEventFormat = TraceEventFormat(startTime, instantEvents)
      Paths.get(traceFilePath).outputStream().writer().use {
        traceEventFormat.write(items, activities, it)
      }
    }
  }
}

private fun computePluginCostMap(): MutableMap<String, ObjectLongHashMap<String>> {
  var result: MutableMap<String, ObjectLongHashMap<String>>
  synchronized(StartUpMeasurer.pluginCostMap) {
    result = THashMap(StartUpMeasurer.pluginCostMap)
    StartUpMeasurer.pluginCostMap.clear()
  }

  for (plugin in PluginManagerCore.getLoadedPlugins()) {
    val id = plugin.pluginId.idString
    val classLoader = (plugin as IdeaPluginDescriptorImpl).pluginClassLoader as? PluginClassLoader ?: continue
    val costPerPhaseMap = result.getOrPut(id) { ObjectLongHashMap() }
    costPerPhaseMap.put("classloading (EDT)", classLoader.edtTime)
    costPerPhaseMap.put("classloading (background)", classLoader.backgroundTime)
  }
  return result
}

// to make output more compact (quite a lot slow components)
internal class MyJsonPrettyPrinter : IntelliJPrettyPrinter() {
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

internal fun isSubItem(item: ActivityImpl, itemIndex: Int, list: List<ActivityImpl>): Boolean {
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