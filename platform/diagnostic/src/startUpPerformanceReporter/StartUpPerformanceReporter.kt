// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.io.jackson.IntelliJPrettyPrinter
import com.intellij.util.io.write
import com.intellij.util.lang.ClassPath
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

class StartUpPerformanceReporter : StartupActivity, StartUpPerformanceService {
  init {
    // Since the measurement requires OptionsTopHitProvider.Activity to fire lastOptionTopHitProviderFinishedForProject,
    // and OptionsTopHitProvider.Activity is not available in test or headless mode:
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.INSTANCE
    }
  }

  private var startUpFinishedCounter = AtomicInteger()

  private var pluginCostMap: Map<String, Object2LongMap<String>>? = null

  private var lastReport: ByteBuffer? = null
  private var lastMetrics: Object2IntMap<String>? = null

  companion object {
    internal val LOG = logger<StartUpMeasurer>()

    internal const val VERSION = "34"

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

    private fun doLogStats(projectName: String): StartUpPerformanceReporterValues {
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

      val pluginCostMap = computePluginCostMap()

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

      val perfFilePath = System.getProperty("idea.log.perf.stats.file")
      if (!perfFilePath.isNullOrBlank()) {
        val app = ApplicationManager.getApplication()
        if (!app.isUnitTestMode &&
            SystemProperties.getBooleanProperty("idea.log.perf.stats",
                                                app.isInternal ||
                                                ApplicationInfoEx.getInstanceEx().build.isSnapshot)) {
          w.writeToLog(LOG)
        }

        LOG.info("StartUp Measurement report was written to: $perfFilePath")
        Path.of(perfFilePath).write(currentReport)
      }

      val classReport = System.getProperty("idea.log.class.list.file")
      if (!classReport.isNullOrBlank()) {
        generateJarAccessLog(Path.of(FileUtil.expandUserHome(classReport)))
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
      reportIfAnotherAlreadySet(projectName)
    }
  }

  override fun lastOptionTopHitProviderFinishedForProject(project: Project) {
    reportIfAnotherAlreadySet(project.name)
  }

  override fun reportStatistics(project: Project) {
    NonUrgentExecutor.getInstance().execute {
      logStats(project.name)
    }
  }

  private fun reportIfAnotherAlreadySet(projectName: String) {
    // or StartUpPerformanceReporter activity will be finished first, or OptionsTopHitProvider.Activity
    if (startUpFinishedCounter.incrementAndGet() == 2) {
      startUpFinishedCounter.set(0)
      StartUpMeasurer.stopPluginCostMeasurement()
      // Don't report statistic from here if we want to measure project import duration
      if (SystemProperties.getBooleanProperty("idea.collect.project.import.performance", false)) return
      // even if this activity executed in a pooled thread, better if it will not affect start-up in any way
      NonUrgentExecutor.getInstance().execute {
        logStats(projectName)
      }
    }
  }

  @Synchronized
  private fun logStats(projectName: String) {
    val params = doLogStats(projectName)
    pluginCostMap = params.pluginCostMap
    lastReport = params.lastReport
    lastMetrics = params.lastMetrics
  }
}

private class StartUpPerformanceReporterValues(val pluginCostMap: MutableMap<String, Object2LongOpenHashMap<String>>,
                                               val lastReport: ByteBuffer,
                                               val lastMetrics: Object2IntMap<String>)

private fun computePluginCostMap(): MutableMap<String, Object2LongOpenHashMap<String>> {
  val result = HashMap(StartUpMeasurer.pluginCostMap)
  StartUpMeasurer.pluginCostMap.clear()

  for (plugin in PluginManagerCore.getLoadedPlugins()) {
    val id = plugin.pluginId.idString
    val classLoader = (plugin as IdeaPluginDescriptorImpl).pluginClassLoader as? PluginAwareClassLoader ?: continue
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
  val classLoader = StartUpPerformanceReporter::class.java.classLoader
  @Suppress("UNCHECKED_CAST")
  val itemsFromBootstrap = MethodHandles.lookup()
    .findStatic(classLoader::class.java, "getLoadedClasses", MethodType.methodType(Collection::class.java))
    .invokeExact() as Collection<Map.Entry<String, Path>>
  val itemsFromCore = ClassPath.getLoadedClasses()
  val items = LinkedHashSet<Map.Entry<String, Path>>(itemsFromBootstrap.size + itemsFromCore.size)
  items.addAll(itemsFromBootstrap)
  items.addAll(itemsFromCore)

  val homeDir = Path.of(PathManager.getHomePath())

  val builder = StringBuilder()
  for (item in items) {
    val source = item.value
    if (!source.startsWith(homeDir)) {
      continue
    }

    builder.append(item.key).append(':').append(homeDir.relativize(source))
    builder.append('\n')
  }
  Files.createDirectories(outFile.parent)
  Files.writeString(outFile, builder)
}
