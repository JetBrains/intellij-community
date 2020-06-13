// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpMeasurer.Activities
import com.intellij.diagnostic.StartUpPerformanceService
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.io.jackson.IntelliJPrettyPrinter
import com.intellij.util.io.outputStream
import com.intellij.util.io.write
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.Comparator

class StartUpPerformanceReporter : StartupActivity, StartUpPerformanceService {
  private var startUpFinishedCounter = AtomicInteger()

  private var pluginCostMap: Map<String, Object2LongMap<String>>? = null

  private var lastReport: ByteBuffer? = null
  private var lastMetrics: Object2IntMap<String>? = null

  companion object {
    internal val LOG = logger<StartUpMeasurer>()

    internal const val VERSION = "22"

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

    fun logStats(projectName: String) {
      doLogStats(projectName)
    }

    private fun doLogStats(projectName: String): StartUpPerformanceReporterValues? {
      val items = mutableListOf<ActivityImpl>()
      val instantEvents = mutableListOf<ActivityImpl>()
      val activities = Object2ObjectOpenHashMap<String, MutableList<ActivityImpl>>()
      val serviceActivities = Object2ObjectOpenHashMap<String, MutableList<ActivityImpl>>()
      val services = mutableListOf<ActivityImpl>()

      val threadNameManager = IdeThreadNameManager()

      var end = -1L

      StartUpMeasurer.processAndClear(SystemProperties.getBooleanProperty("idea.collect.perf.after.first.project", false)) { item ->
        // process it now to ensure that thread will have first name (because report writer can process events in any order)
        threadNameManager.getThreadName(item)

        if (item.end == -1L) {
          instantEvents.add(item)
        }
        else {
          val category = item.category
          if (category == null) {
            items.add(item)
            if (item.name == Activities.PROJECT_DUMB_POST_START_UP_ACTIVITIES) {
              end = item.end
            }
          }
          else if (category == ActivityCategory.APP_COMPONENT ||
                   category == ActivityCategory.PROJECT_COMPONENT ||
                   category == ActivityCategory.MODULE_COMPONENT ||
                   category == ActivityCategory.APP_SERVICE ||
                   category == ActivityCategory.PROJECT_SERVICE ||
                   category == ActivityCategory.MODULE_SERVICE ||
                   category == ActivityCategory.SERVICE_WAITING) {
            services.add(item)
            serviceActivities.getOrPut(category.jsonName) { mutableListOf() }.add(item)
          }
          else {
            activities.getOrPut(category.jsonName) { mutableListOf() }.add(item)
          }
        }
      }

      if (items.isEmpty()) {
        return null
      }

      sortItems(items)

      val pluginCostMap = computePluginCostMap()

      val w = IdeIdeaFormatWriter(activities, pluginCostMap, threadNameManager)
      val startTime = items.first().start
      for (item in items) {
        val pluginId = item.pluginId ?: continue
        StartUpMeasurer.doAddPluginCost(pluginId, item.category?.name ?: "unknown", item.end - item.start, pluginCostMap)
      }

      w.write(startTime, items, serviceActivities, instantEvents, end, projectName)

      val currentReport = w.toByteBuffer()

      val perfFilePath = System.getProperty("idea.log.perf.stats.file")
      val traceFilePath = System.getProperty("idea.log.perf.trace.file")
      val mayLogReport = SystemProperties.getBooleanProperty("idea.log.perf.stats",
                                                             ApplicationManager.getApplication().isInternal
                                                             || ApplicationInfoEx.getInstanceEx().build.isSnapshot)

      if (!perfFilePath.isNullOrBlank() && !ApplicationManager.getApplication().isUnitTestMode && mayLogReport) {
        w.writeToLog(LOG)
      }

      if (!perfFilePath.isNullOrBlank()) {
        LOG.info("StartUp Measurement report was written to: $perfFilePath")
        Paths.get(perfFilePath).write(currentReport)
      }

      if (!traceFilePath.isNullOrBlank()) {
        LOG.info("StartUp trace report was written to: $traceFilePath")
        val traceEventFormat = TraceEventFormatWriter(startTime, instantEvents, threadNameManager)
        Paths.get(traceFilePath).outputStream().writer().use {
          traceEventFormat.write(items, activities, services, it)
        }
      }
      return StartUpPerformanceReporterValues(pluginCostMap, currentReport, w.publicStatMetrics)
    }
  }

  override fun getMetrics() = lastMetrics

  override fun getPluginCostMap() = pluginCostMap!!

  override fun getLastReport() = lastReport

  override fun runActivity(project: Project) {
    if (ActivityImpl.listener != null) {
      return
    }

    val projectName = project.name
    ActivityImpl.listener = ActivityListener(projectName)
  }

  inner class ActivityListener(private val projectName: String) : Consumer<ActivityImpl> {
    @Volatile
    private var projectOpenedActivitiesPassed = false

    // not all activities are performed always, so, we wait only activities that were started
    @Volatile
    private var editorRestoringTillPaint = true

    override fun accept(activity: ActivityImpl) {
      if (activity.category != null && activity.category != ActivityCategory.APP_INIT) {
        return
      }

      if (activity.end == 0L) {
        if (activity.name == Activities.EDITOR_RESTORING_TILL_PAINT) {
          editorRestoringTillPaint = false
        }
      }
      else {
        when (activity.name) {
          Activities.PROJECT_DUMB_POST_START_UP_ACTIVITIES -> {
            projectOpenedActivitiesPassed = true
            if (editorRestoringTillPaint) {
              completed()
            }
          }
          Activities.EDITOR_RESTORING_TILL_PAINT -> {
            editorRestoringTillPaint = true
            if (projectOpenedActivitiesPassed) {
              completed()
            }
          }
        }
      }
    }

    private fun completed() {
      ActivityImpl.listener = null
      reportIfAnotherAlreadySet(projectName)
    }
  }

  override fun lastOptionTopHitProviderFinishedForProject(project: Project) {
    reportIfAnotherAlreadySet(project.name)
  }

  private fun reportIfAnotherAlreadySet(projectName: String) {
    // or StartUpPerformanceReporter activity will be finished first, or OptionsTopHitProvider.Activity
    if (startUpFinishedCounter.incrementAndGet() == 2) {
      startUpFinishedCounter.set(0)
      StartUpMeasurer.stopPluginCostMeasurement()
      // even if this activity executed in a pooled thread, better if it will not affect start-up in any way
      NonUrgentExecutor.getInstance().execute {
        logStats(projectName)
      }
    }
  }

  @Synchronized
  private fun logStats(projectName: String) {
    val params = doLogStats(projectName) ?: return
    pluginCostMap = params.pluginCostMap
    lastReport = params.lastReport
    lastMetrics = params.lastMetrics
  }
}

private class StartUpPerformanceReporterValues(val pluginCostMap: MutableMap<String, Object2LongMap<String>>,
                                               val lastReport: ByteBuffer,
                                               val lastMetrics: Object2IntMap<String>)

private fun computePluginCostMap(): MutableMap<String, Object2LongMap<String>> {
  var result: MutableMap<String, Object2LongMap<String>>
  synchronized(StartUpMeasurer.pluginCostMap) {
    result = Object2ObjectOpenHashMap(StartUpMeasurer.pluginCostMap)
    StartUpMeasurer.pluginCostMap.clear()
  }

  for (plugin in PluginManagerCore.getLoadedPlugins()) {
    val id = plugin.pluginId.idString
    val classLoader = (plugin as IdeaPluginDescriptorImpl).pluginClassLoader as? PluginClassLoader ?: continue
    val costPerPhaseMap = result.getOrPut(id) {
      val m = Object2LongOpenHashMap<String>()
      m.defaultReturnValue(-1)
      m
    }
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

internal fun compareTime(o1: ActivityImpl, o2: ActivityImpl): Int {
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