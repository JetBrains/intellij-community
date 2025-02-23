// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpMeasurer.Activities
import com.intellij.diagnostic.StartUpPerformanceService
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.TelemetryManagerImpl
import com.intellij.util.SystemProperties
import com.intellij.util.io.createParentDirectories
import com.intellij.util.lang.ClassPath
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import kotlin.io.path.invariantSeparatorsPathString

private val LOG: Logger
  get() = logger<StartUpMeasurer>()

@Internal
open class StartUpPerformanceReporter(private val coroutineScope: CoroutineScope) : StartUpPerformanceService {
  private var pluginCostMap: Map<String, Object2LongMap<String>>? = null

  private var lastReport: ByteBuffer? = null
  private var lastMetrics = MutableSharedFlow<Object2IntMap<String>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  companion object {
    @JvmStatic
    protected val perfFilePath: String? = System.getProperty("idea.log.perf.stats.file")?.takeIf(String::isNotEmpty)

    internal const val VERSION: String = "38"

    suspend fun logStats(projectName: String) {
      logAndClearStats(projectName, perfFilePath)
    }
  }

  override fun getMetrics(): Flow<Object2IntMap<String>> = lastMetrics

  override fun getPluginCostMap(): Map<String, Object2LongMap<String>> = pluginCostMap ?: emptyMap()

  override fun getLastReport(): ByteBuffer? = lastReport

  override fun reportStatistics(project: Project) {
    keepAndLogStats(project.name)
  }

  private val reportMutex = Mutex()

  protected fun keepAndLogStats(projectName: String) {
    coroutineScope.launch {
      reportMutex.withLock {
        val params = logAndClearStats(projectName, perfFilePath)
        pluginCostMap = params.pluginCostMap
        lastReport = params.lastReport
        lastMetrics.emit(params.lastMetrics)
      }
    }
  }
}

private suspend fun logAndClearStats(projectName: String, perfFilePath: String?): StartUpPerformanceReporterValues {
  val instantEvents = mutableListOf<ActivityImpl>()
  // write activity category in the same order as first reported
  val activities = LinkedHashMap<String, MutableList<ActivityImpl>>()
  val serviceActivities = HashMap<String, MutableList<ActivityImpl>>()
  val services = mutableListOf<ActivityImpl>()

  val threadNameManager = IdeThreadNameManager()

  var end = -1L

  StartUpMeasurer.processAndClear(SystemProperties.getBooleanProperty("idea.collect.perf.after.first.project", false)) { item ->
    // process it now to ensure that a thread will have a first name (because a report writer can process events in any order)
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

  (TelemetryManager.getInstance() as? TelemetryManagerImpl)
    ?.addStartupActivities((activities.get(ActivityCategory.DEFAULT.jsonName) ?: emptyList()).sortedWith(itemComparator))

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

  if (perfFilePath != null) {
    LOG.info("StartUp Measurement report was written to: $perfFilePath")
    withContext(Dispatchers.IO) {
      Files.newByteChannel(Path.of(perfFilePath).createParentDirectories(), StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
        it.write(currentReport)
      }
    }
    currentReport.flip()
  }

  val classReport = System.getProperty("idea.log.class.list.file")
  if (!classReport.isNullOrBlank()) {
    generateJarAccessLog(Path.of(FileUtil.expandUserHome(classReport)))
  }

  for (instantEvent in instantEvents.filter { setOf("splash shown", "splash hidden").contains(it.name) }) {
    val key = "event:${instantEvent.name}"
    w.publicStatMetrics[key] = TimeUnit.NANOSECONDS.toMillis(instantEvent.start - StartUpMeasurer.getStartTime()).toInt()
  }

  return StartUpPerformanceReporterValues(pluginCostMap, currentReport, w.publicStatMetrics)
}

private class StartUpPerformanceReporterValues(
  @JvmField val pluginCostMap: MutableMap<String, Object2LongOpenHashMap<String>>,
  @JvmField val lastReport: ByteBuffer,
  @JvmField val lastMetrics: Object2IntMap<String>,
)

private fun computePluginCostMap(): MutableMap<String, Object2LongOpenHashMap<String>> {
  val result = HashMap(StartUpMeasurer.pluginCostMap)
  StartUpMeasurer.pluginCostMap.clear()

  for (plugin in PluginManagerCore.loadedPlugins) {
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

private fun generateJarAccessLog(outFile: Path) {
  val homeDir = Path.of(PathManager.getHomePath())
  val builder = StringBuilder()
  for (item in ClassPath.getLoadedClasses()) {
    val source = item.value
    if (!source.startsWith(homeDir)) {
      continue
    }
    builder.append(item.key).append(':').append(homeDir.relativize(source).invariantSeparatorsPathString)
    builder.append('\n')
  }
  Files.createDirectories(outFile.parent)
  Files.writeString(outFile, builder)
}

private class HeadlessStartUpPerformanceService : StartUpPerformanceService {
  override fun reportStatistics(project: Project) { }

  override fun getPluginCostMap(): Map<String, Object2LongMap<String>> = emptyMap()

  override fun getMetrics(): Flow<Object2IntMap<String>> = emptyFlow()

  override fun getLastReport(): ByteBuffer? = null
}
