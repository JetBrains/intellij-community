// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.ActivityImpl
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.util.containers.ObjectLongHashMap
import com.intellij.util.io.jackson.obj
import gnu.trove.THashMap

// events must be already sorted by time
internal fun computeOwnTime(allEvents: List<ActivityImpl>, threadNameManager: ThreadNameManager): ObjectLongHashMap<ActivityImpl> {
  val ownDurations = ObjectLongHashMap<ActivityImpl>()

  val threadToList = THashMap<String, MutableList<ActivityImpl>>()
  for (event in allEvents) {
    threadToList.getOrPut(threadNameManager.getThreadName(event)) { mutableListOf() }.add(event)
  }

  val respectedItems = mutableListOf<ActivityImpl>()

  for (list in threadToList.values) {
    for ((index, item) in list.withIndex()) {
      if (item.category == ActivityCategory.SERVICE_WAITING) {
        continue
      }

      val totalDuration = item.end - item.start
      var ownDuration = totalDuration
      respectedItems.clear()

      if (index > 0 && list.get(index - 1).start > item.start) {
        StartUpPerformanceReporter.LOG.error("prev ${list.get(index - 1).name} start > ${item.name}")
      }

      for (i in (index + 1) until list.size) {
        val otherItem = list.get(i)
        if (otherItem.end > item.end) {
          break
        }

        if (isInclusive(otherItem, item) && !respectedItems.any { isInclusive(otherItem, it) }) {
          ownDuration -= otherItem.end - otherItem.start
          respectedItems.add(otherItem)
        }
      }

      if (totalDuration != ownDuration) {
        ownDurations.put(item, ownDuration)
      }
    }
  }

  return ownDurations
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