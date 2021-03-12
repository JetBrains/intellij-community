// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.ThreadNameManager
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import com.intellij.util.lang.ClassPath
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
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
                                   threadNameManager: ThreadNameManager) : IdeaFormatWriter(activities, threadNameManager, StartUpPerformanceReporter.VERSION) {
  val publicStatMetrics = Object2IntOpenHashMap<String>()

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

  override fun writeItemTimeInfo(item: ActivityImpl, duration: Long, offset: Long, writer: JsonGenerator) {
    if (item.name == "bootstrap" || item.name == "app initialization") {
      publicStatMetrics.put(item.name, TimeUnit.NANOSECONDS.toMillis(duration).toInt())
    }
    super.writeItemTimeInfo(item, duration, offset, writer)
  }

  override fun writeTotalDuration(writer: JsonGenerator, totalDuration: Long, end: Long, timeOffset: Long): Long {
    val totalDurationActual = super.writeTotalDuration(writer, totalDuration, end, timeOffset)
    publicStatMetrics.put("totalDuration", totalDurationActual.toInt())
    return totalDurationActual
  }

  override fun beforeActivityWrite(item: ActivityImpl, ownOrTotalDuration: Long, fieldName: String) {
    item.pluginId?.let {
      StartUpMeasurer.doAddPluginCost(it, item.category?.name ?: "unknown", ownOrTotalDuration, pluginCostMap)
    }

    if (fieldName == "prepareAppInitActivities" && item.name == "splash initialization") {
      publicStatMetrics.put("splash", TimeUnit.NANOSECONDS.toMillis(ownOrTotalDuration).toInt())
    }
  }
}

private fun writeIcons(writer: JsonGenerator) {
  writer.array("icons") {
    for (stat in IconLoadMeasurer.getStats()) {
      writer.obj {
        writer.writeStringField("name", stat.name)
        writer.writeNumberField("count", stat.count)
        writer.writeNumberField("time", TimeUnit.NANOSECONDS.toMillis(stat.totalDuration))
      }
    }
  }
}

private fun safeHashValue(value: String): String {
  val generator = Argon2BytesGenerator()
  generator.init(Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).build())
  // 160 bit is enough for uniqueness
  val result = ByteArray(20)
  generator.generateBytes(value.toByteArray(), result, 0, result.size)
  return Base64.getEncoder().withoutPadding().encodeToString(result)
}

private fun writeServiceStats(writer: JsonGenerator) {
  class StatItem(val name: String) {
    var app = 0
    var project = 0
    var module = 0
  }

  // components can be inferred from data, but to verify that items reported correctly (and because for items threshold is applied (not all are reported))
  val component = StatItem("component")
  val service = StatItem("service")

  val plugins = PluginManagerCore.getLoadedPlugins(null).sortedBy { it.pluginId }
  for (plugin in plugins) {
    service.app += (plugin as IdeaPluginDescriptorImpl).app.services.size
    service.project += plugin.project.services.size
    service.module += plugin.module.services.size

    component.app += plugin.app.components?.size ?: 0
    component.project += plugin.project.components?.size ?: 0
    component.module += plugin.module.components?.size ?: 0
  }

  writer.obj("stats") {
    writer.writeNumberField("plugin", plugins.size)
    for (statItem in listOf(component, service)) {
      writer.obj(statItem.name) {
        writer.writeNumberField("app", statItem.app)
        writer.writeNumberField("project", statItem.project)
        writer.writeNumberField("module", statItem.module)
      }
    }
  }

  writer.array("plugins") {
    for (plugin in plugins) {
      val classLoader = plugin.pluginClassLoader as? PluginAwareClassLoader ?: continue
      writer.obj {
        writer.writeStringField("id", plugin.pluginId.idString)
        writer.writeNumberField("classCount", classLoader.loadedClassCount)
        writer.writeNumberField("classLoadingEdtTime", TimeUnit.NANOSECONDS.toMillis(classLoader.edtTime))
        writer.writeNumberField("classLoadingBackgroundTime", TimeUnit.NANOSECONDS.toMillis(classLoader.backgroundTime))
      }
    }
  }
}