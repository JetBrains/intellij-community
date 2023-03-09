// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpMeasurer.Activities
import com.intellij.diagnostic.StartUpPerformanceService
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import com.intellij.util.io.jackson.IntelliJPrettyPrinter
import com.intellij.util.io.write
import com.intellij.util.lang.ClassPath
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

open class StartUpPerformanceReporter : StartUpPerformanceService {
  private var pluginCostMap: Map<String, Object2LongMap<String>>? = null

  private var lastReport: ByteBuffer? = null
  private var lastMetrics: Object2IntMap<String>? = null

  companion object {
    @JvmStatic
    protected val perfFilePath = System.getProperty("idea.log.perf.stats.file")?.takeIf(String::isNotEmpty)

    internal val LOG = logger<StartUpMeasurer>()

    internal const val VERSION = "38"

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
      logAndClearStats(projectName, perfFilePath)
    }

    private class ActivityListener(
      private val projectName: String,
      private val manager: StartUpPerformanceReporter,
    ) : Consumer<ActivityImpl> {
      @Volatile
      private var projectOpenedActivitiesPassed = false

      // not all activities are performed always, so, we wait only activities that were started
      @Volatile
      private var editorRestoringTillPaint = true

      override fun accept(activity: ActivityImpl) {
        if (activity.category != null && activity.category != ActivityCategory.DEFAULT) {
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

        StartUpMeasurer.stopPluginCostMeasurement()
        // don't report statistic from here if we want to measure project import duration
        if (!java.lang.Boolean.getBoolean("idea.collect.project.import.performance")) {
          manager.keepAndLogStats(projectName)
        }
      }
    }
  }

  override fun getMetrics() = lastMetrics

  override fun getPluginCostMap() = pluginCostMap!!

  override fun getLastReport() = lastReport

  override fun addActivityListener(project: Project) {
    if (ActivityImpl.listener == null) {
      ActivityImpl.listener = ActivityListener(project.name, this)
    }
  }

  override fun reportStatistics(project: Project) {
    project.coroutineScope.launch {
      keepAndLogStats(project.name)
    }
  }

  @Synchronized
  private fun keepAndLogStats(projectName: String) {
    val params = logAndClearStats(projectName, perfFilePath)
    pluginCostMap = params.pluginCostMap
    lastReport = params.lastReport
    lastMetrics = params.lastMetrics
  }
}

private fun logAndClearStats(projectName: String, perfFilePath: String?): StartUpPerformanceReporterValues {
  val instantEvents = mutableListOf<ActivityImpl>()
  // write activity category in the same order as first reported
  val activities = LinkedHashMap<String, MutableList<ActivityImpl>>()
  val serviceActivities = HashMap<String, MutableList<ActivityImpl>>()
  val services = mutableListOf<ActivityImpl>()

  val threadNameManager = IdeThreadNameManager()

  var end = -1L

  StartUpMeasurer.processAndClear(SystemProperties.getBooleanProperty("idea.collect.perf.after.first.project", false)) { item ->
    // process it now to ensure that thread will have a first name (because report writer can process events in any order)
    threadNameManager.getThreadName(item)

    if (item.end == -1L) {
      instantEvents.add(item)
    }
    else {
      when (val category = item.category ?: ActivityCategory.DEFAULT) {
        ActivityCategory.DEFAULT -> {
          if (item.name == Activities.PROJECT_DUMB_POST_START_UP_ACTIVITIES) {
            end = item.end
          }
          activities.computeIfAbsent(category.jsonName) { mutableListOf() }.add(item)
        }
        ActivityCategory.APP_COMPONENT, ActivityCategory.PROJECT_COMPONENT, ActivityCategory.MODULE_COMPONENT,
        ActivityCategory.APP_SERVICE, ActivityCategory.PROJECT_SERVICE, ActivityCategory.MODULE_SERVICE,
        ActivityCategory.SERVICE_WAITING -> {
          services.add(item)
          serviceActivities.computeIfAbsent(category.jsonName) { mutableListOf() }.add(item)
        }
        else -> {
          activities.computeIfAbsent(category.jsonName) { mutableListOf() }.add(item)
        }
      }
    }
  }

  val pluginCostMap: MutableMap<String, Object2LongMap<String>> = computePluginCostMap()

  val w = IdeIdeaFormatWriter(activities, pluginCostMap, threadNameManager)
  val defaultActivities = activities.get(ActivityCategory.DEFAULT.jsonName)
  val startTime = defaultActivities?.first()?.start ?: 0
  if (defaultActivities != null) {
    for (item in defaultActivities) {
      val pluginId = item.pluginId ?: continue
      StartUpMeasurer.doAddPluginCost(pluginId, item.category?.name ?: "unknown", item.end - item.start, pluginCostMap)
    }
  }

  w.write(startTime, serviceActivities, instantEvents, end, projectName)

  val currentReport = w.toByteBuffer()

  if (System.getProperty("idea.log.perf.stats", "false").toBoolean()) {
    w.writeToLog(StartUpPerformanceReporter.LOG)
  }

  if (perfFilePath != null) {
    StartUpPerformanceReporter.LOG.info("StartUp Measurement report was written to: $perfFilePath")
    Path.of(perfFilePath).write(currentReport)
    currentReport.flip()
  }

  val classReport = System.getProperty("idea.log.class.list.file")
  if (!classReport.isNullOrBlank()) {
    generateJarAccessLog(Path.of(FileUtil.expandUserHome(classReport)))
  }

  for (instantEvent in instantEvents.filter { setOf("splash shown", "splash hidden").contains(it.name) }) {
    w.publicStatMetrics.put("event:${instantEvent.name}",
                            TimeUnit.NANOSECONDS.toMillis(instantEvent.start - StartUpMeasurer.getStartTime()).toInt())
  }

  return StartUpPerformanceReporterValues(pluginCostMap, currentReport, w.publicStatMetrics)
}

private class StartUpPerformanceReporterValues(val pluginCostMap: MutableMap<String, Object2LongMap<String>>,
                                               val lastReport: ByteBuffer,
                                               val lastMetrics: Object2IntMap<String>)

private fun computePluginCostMap(): MutableMap<String, Object2LongMap<String>> {
  val result = HashMap(StartUpMeasurer.pluginCostMap)
  StartUpMeasurer.pluginCostMap.clear()

  for (plugin in PluginManagerCore.getLoadedPlugins()) {
    val id = plugin.pluginId.idString
    val classLoader = (plugin as IdeaPluginDescriptorImpl).pluginClassLoader as? PluginAwareClassLoader ?: continue
    val costPerPhaseMap = result.computeIfAbsent(id) {
      val m = Object2LongOpenHashMap<String>()
      m.defaultReturnValue(-1)
      m
    }
    costPerPhaseMap.put("classloading (EDT)", classLoader.edtTime)
    costPerPhaseMap.put("classloading (background)", classLoader.backgroundTime)
  }
  return result
}

// to make output more compact (quite a lot of slow components)
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

private fun generateJarAccessLog(outFile: Path) {
  val homeDir = Path.of(PathManager.getHomePath())
  val builder = StringBuilder()
  for (item in ClassPath.getLoadedClasses()) {
    val source = item.value
    if (!source.startsWith(homeDir)) {
      continue
    }

    builder.append(item.key).append(':').append(homeDir.relativize(source).toString().replace(File.separatorChar, '/'))
    builder.append('\n')
  }
  Files.createDirectories(outFile.parent)
  Files.writeString(outFile, builder)
}

private class HeadlessStartUpPerformanceService : StartUpPerformanceService {
  override fun reportStatistics(project: Project) {
  }

  override fun getPluginCostMap(): Map<String, Object2LongMap<String>> = emptyMap()

  override fun getMetrics(): Object2IntMap<String>? = null

  override fun getLastReport(): ByteBuffer? = null

  override fun addActivityListener(project: Project) {
  }
}