// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.ThreadNameManager
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2LongMap
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.lang.management.ManagementFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

internal class IdeIdeaFormatWriter(activities: Map<String, MutableList<ActivityImpl>>,
                                   private val pluginCostMap: MutableMap<String, Object2LongMap<String>>,
                                   threadNameManager: ThreadNameManager) : IdeaFormatWriter(activities, threadNameManager) {
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
    writer.writeStringField("project", safeHashValue(projectName))
  }

  override fun writeExtraData(writer: JsonGenerator) {
    writeServiceStats(writer)
    writeIcons(writer)
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
        writer.writeStringField("name", stat.type)
        writer.writeNumberField("count", stat.counter)
        writer.writeNumberField("time", TimeUnit.NANOSECONDS.toMillis(stat.totalTime))
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

  val plugins = PluginManagerCore.getLoadedPlugins()
  for (plugin in plugins) {
    service.app += (plugin as IdeaPluginDescriptorImpl).app.services.size
    service.project += plugin.project.services.size
    service.module += plugin.module.services.size

    component.app += plugin.app.components.size
    component.project += plugin.project.components.size
    component.module += plugin.module.components.size
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

    writer.obj("loadedClasses") {
      for (plugin in plugins) {
        val classLoader = (plugin as IdeaPluginDescriptorImpl).pluginClassLoader as? PluginClassLoader ?: continue
        val classCount = classLoader.loadedClassCount
        if (classCount > 0) {
          writer.writeNumberField(plugin.pluginId.idString, classCount)
        }
      }
    }
  }
}