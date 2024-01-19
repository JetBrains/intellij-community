// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
package com.intellij.platform.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.ThreadNameManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.tree.IElementType
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import com.intellij.util.io.sha3_512
import com.intellij.util.lang.ClassPath
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.management.ManagementFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

internal class IdeIdeaFormatWriter(activities: Map<String, MutableList<ActivityImpl>>,
                                   private val pluginCostMap: MutableMap<String, Object2LongOpenHashMap<String>>,
                                   threadNameManager: ThreadNameManager) : IdeaFormatWriter(activities, threadNameManager,
                                                                                            StartUpPerformanceReporter.VERSION) {
  val publicStatMetrics: Object2IntOpenHashMap<String> = Object2IntOpenHashMap<String>()

  init {
    publicStatMetrics.defaultReturnValue(-1)
  }

  fun writeToLog(log: Logger) {
    stringWriter.write("\n=== Stop: StartUp Measurement ===")
    log.info(stringWriter.toString())
  }

  override fun writeAppInfo(writer: JsonGenerator) {
    val appInfo = ApplicationInfo.getInstance()
    writer.writeStringField("build", appInfo.build.asStringWithoutProductCode())
    writer.writeStringField("buildDate", ZonedDateTime.ofInstant(appInfo.buildDate.toInstant(), ZoneId.systemDefault()).format(DateTimeFormatter.RFC_1123_DATE_TIME))
    writer.writeStringField("productCode", appInfo.build.productCode)

    // see CDSManager from platform-impl
    @Suppress("SpellCheckingInspection")
    if (ManagementFactory.getRuntimeMXBean().inputArguments.any { it == "-Xshare:auto" || it == "-Xshare:on" }) {
      writer.writeBooleanField("cds", true)
    }
  }

  override fun writeProjectName(writer: JsonGenerator, projectName: String) {
    writer.writeStringField("project", System.getProperty("idea.performanceReport.projectName") ?: safeHashValue(projectName))
  }

  override fun writeExtraData(writer: JsonGenerator) {
    val stats = getClassAndResourceLoadingStats()
    writer.obj("classLoading") {
      val time = stats.getValue("classLoadingTime")
      writer.writeNumberField("time", TimeUnit.NANOSECONDS.toMillis(time))
      val defineTime = stats.getValue("classDefineTime")
      writer.writeNumberField("searchTime", TimeUnit.NANOSECONDS.toMillis(time - defineTime))
      writer.writeNumberField("defineTime", TimeUnit.NANOSECONDS.toMillis(defineTime))
      writer.writeNumberField("count", stats.getValue("classRequests"))
    }
    writer.obj("resourceLoading") {
      writer.writeNumberField("time", TimeUnit.NANOSECONDS.toMillis(stats.getValue("resourceLoadingTime")))
      writer.writeNumberField("count", stats.getValue("resourceRequests"))
    }
    writer.obj("langLoading") {
      val allTypes = IElementType.enumerate(IElementType.TRUE)
      writer.writeNumberField("elementTypeCount", allTypes.size)
    }

    writer.obj("jvm") {
      val bootstrapTime = (StartUpMeasurer.getStartTimeUnixNanoDiff() + StartUpMeasurer.getStartTime()) / 1_000_000
      writer.writeNumberField("loadingTime", bootstrapTime - ManagementFactory.getRuntimeMXBean().startTime)
    }
    writeServiceStats(writer)
    writeIcons(writer)
  }

  private fun getClassAndResourceLoadingStats(): Map<String, Long> {
    // data from bootstrap classloader
    val classLoader = IdeIdeaFormatWriter::class.java.classLoader
    @Suppress("UNCHECKED_CAST")
    val stats = MethodHandles.lookup()
      .findVirtual(classLoader::class.java, "getLoadingStats", MethodType.methodType(Map::class.java))
      .bindTo(classLoader).invokeExact() as MutableMap<String, Long>

    // data from core classloader
    val coreStats = ClassPath.getLoadingStats()
    if (coreStats.get("identity") != stats.get("identity")) {
      for (entry in coreStats.entries) {
        val v1 = stats.getValue(entry.key)
        if (v1 != entry.value) {
          stats.put(entry.key, v1 + entry.value)
        }
      }
    }

    return stats
  }

  override fun writeTotalDuration(writer: JsonGenerator, end: Long, timeOffset: Long): Long {
    val totalDurationActual = super.writeTotalDuration(writer, end, timeOffset)
    publicStatMetrics.put("totalDuration", totalDurationActual.toInt())
    return totalDurationActual
  }

  override fun beforeActivityWrite(item: ActivityImpl, ownOrTotalDuration: Long, fieldName: String) {
    item.pluginId?.let {
      StartUpMeasurer.doAddPluginCost(it, item.category?.name ?: "unknown", ownOrTotalDuration, pluginCostMap)
    }

    if (fieldName == "items") {
      when (val itemName = item.name) {
        "splash initialization" -> {
          publicStatMetrics["splash"] = TimeUnit.NANOSECONDS.toMillis(ownOrTotalDuration).toInt()
        }
        "bootstrap", "app initialization" -> {
          publicStatMetrics[itemName] = TimeUnit.NANOSECONDS.toMillis(ownOrTotalDuration).toInt()
        }
        "project frame initialization" -> {
          publicStatMetrics["projectFrameVisible"] = TimeUnit.NANOSECONDS.toMillis(item.start - StartUpMeasurer.getStartTime()).toInt()
        }
      }
    }
  }
}

private fun writeIcons(writer: JsonGenerator) {
  writer.array("icons") {
    for (stat in IconLoadMeasurer.stats) {
      writer.obj {
        writer.writeStringField("name", stat.name)
        writer.writeNumberField("count", stat.count)
        writer.writeNumberField("time", TimeUnit.NANOSECONDS.toMillis(stat.getTotalDuration()))
      }
    }
  }
}

private fun safeHashValue(value: String): String {
  // 160 bit is enough for uniqueness
  val result = sha3_512().digest(value.toByteArray()).copyOf(20)
  return Base64.getUrlEncoder().withoutPadding().encodeToString(result)
}

private fun writeServiceStats(writer: JsonGenerator) {
  class StatItem(val name: String) {
    var app = 0
    var project = 0
    var module = 0
  }

  // components can be inferred from data,
  // but to verify that item reported correctly (and because for an item threshold is applied (not all are reported))
  val component = StatItem("component")
  val service = StatItem("service")

  val pluginSet = PluginManagerCore.getPluginSet()
  for (plugin in pluginSet.getEnabledModules()) {
    service.app += plugin.appContainerDescriptor.services.size
    service.project += plugin.projectContainerDescriptor.services.size
    service.module += plugin.moduleContainerDescriptor.services.size

    component.app += plugin.appContainerDescriptor.components?.size ?: 0
    component.project += plugin.projectContainerDescriptor.components?.size ?: 0
    component.module += plugin.moduleContainerDescriptor.components?.size ?: 0
  }

  writer.obj("stats") {
    writer.writeNumberField("plugin", pluginSet.enabledPlugins.size)
    for (statItem in listOf(component, service)) {
      writer.obj(statItem.name) {
        writer.writeNumberField("app", statItem.app)
        writer.writeNumberField("project", statItem.project)
        writer.writeNumberField("module", statItem.module)
      }
    }
  }

  writer.array("plugins") {
    for (plugin in pluginSet.enabledPlugins) {
      val classLoader = plugin.pluginClassLoader as? PluginAwareClassLoader ?: continue
      if (classLoader.loadedClassCount == 0L) {
        continue
      }

      writer.obj {
        writer.writeStringField("id", plugin.pluginId.idString)
        writer.writeNumberField("classCount", classLoader.loadedClassCount)
        writer.writeNumberField("classLoadingEdtTime", TimeUnit.NANOSECONDS.toMillis(classLoader.edtTime))
        writer.writeNumberField("classLoadingBackgroundTime", TimeUnit.NANOSECONDS.toMillis(classLoader.backgroundTime))
      }
    }
  }
}