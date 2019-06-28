// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityImpl
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.util.containers.ObjectLongHashMap
import com.intellij.util.io.jackson.obj
import java.util.concurrent.TimeUnit

internal fun computeOwnTime(list: MutableList<ActivityImpl>, ownDurations: ObjectLongHashMap<ActivityImpl>) {
  val respectedItems = mutableListOf<ActivityImpl>()
  var computedDurationForAll = 0L
  for ((index, item) in list.withIndex()) {
    val totalDuration = item.end - item.start
    var ownDuration = totalDuration
    respectedItems.clear()

    if (index > 0 && list.get(index - 1).start > item.start) {
      LOG.warn("prev ${list.get(index - 1).name} start > ${item.name}")
    }

    for (j in (index + 1) until list.size) {
      val otherItem = list.get(j)
      if (otherItem.end > item.end) {
        break
      }

      if (isInclusive(otherItem, item) && respectedItems.all { !isInclusive(otherItem, it) }) {
        ownDuration -= otherItem.end - otherItem.start
        respectedItems.add(otherItem)
      }
    }

    computedDurationForAll += ownDuration
    if (totalDuration != ownDuration) {
      ownDurations.put(item, ownDuration)
    }
  }

  val actualTotalDurationForAll = list.last().end - list.first().start
  val diff = actualTotalDurationForAll - computedDurationForAll
  val diffInMs = TimeUnit.NANOSECONDS.toMillis(diff)
  if (diff < 0 || diffInMs > 3) {
    LOG.debug("computed: $computedDurationForAll, actual: ${actualTotalDurationForAll} (diff: $diff, diffInMs: $diffInMs)")
  }
}

private fun isInclusive(otherItem: ActivityImpl, item: ActivityImpl): Boolean {
  return otherItem.start >= item.start && otherItem.end <= item.end
}

internal fun writeServiceStats(writer: JsonGenerator) {
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
    service.app += (plugin as IdeaPluginDescriptorImpl).appServices.size
    service.project += plugin.projectServices.size
    service.module += plugin.moduleServices.size

    component.app += plugin.appComponents.size
    component.project += plugin.projectComponents.size
    component.module += plugin.moduleComponents.size
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