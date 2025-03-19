// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.plugins

import com.intellij.ide.plugins.DisabledPluginsState.Companion.getDisabledIds
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.application.migrations.getMigrationInstalledPluginIds

internal class PluginsUsagesCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("plugins", 11)
  private val DISABLED_PLUGIN = GROUP.registerEvent("disabled.plugin", EventFields.PluginInfo)
  private val ENABLED_NOT_BUNDLED_PLUGIN = GROUP.registerEvent("enabled.not.bundled.plugin", EventFields.PluginInfo)
  private val UNSAFE_PLUGIN = GROUP.registerEvent("unsafe.plugin",
                                                  EventFields.String("unsafe_id", emptyList()), EventFields.Boolean("enabled"))

  private val MIGRATION_INSTALLED_PLUGIN = GROUP.registerEvent("migration.installed.plugin", EventFields.PluginInfo)
  private val INCOMPATIBLE_PLUGIN = GROUP.registerEvent("incompatible.plugin", EventFields.PluginInfo)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): HashSet<MetricEvent> = HashSet<MetricEvent>().apply {
    addAll(getDisabledPlugins())
    addAll(getEnabledNonBundledPlugins())
    addAll(getNotBundledPlugins())
    addAll(getMigrationInstalledPlugins())
    addAll(getIncompatiblePlugins())
  }

  private fun getIncompatiblePlugins(): Collection<MetricEvent> {
    return PluginManagerCore.getPluginSet().allPlugins
      .filter { !it.isBundled && isIncompatible(it) }
      .map { INCOMPATIBLE_PLUGIN.metric(getPluginInfoById(it.pluginId)) }
  }

  private fun isIncompatible(it: IdeaPluginDescriptorImpl): Boolean {
    return runCatching {
      PluginManagerCore.isIncompatible(it) // sometimes we cannot parse build numbers
    }.getOrDefault(true)
  }

  private fun getMigrationInstalledPlugins(): Collection<MetricEvent> {
    return getMigrationInstalledPluginIds()
      .distinct()
      .map { MIGRATION_INSTALLED_PLUGIN.metric(getPluginInfoById(it)) }
      .toSet()
  }

  private fun getDisabledPlugins(): Set<MetricEvent> {
    return getDisabledIds()
      .map { DISABLED_PLUGIN.metric(getPluginInfoById(it)) }
      .toSet()
  }

  private fun getEnabledNonBundledPlugins(): Set<MetricEvent> {
    return PluginManagerCore.loadedPlugins
      .filter { it.isEnabled && !it.isBundled }
      .map { getPluginInfoByDescriptor(it) }
      .map(ENABLED_NOT_BUNDLED_PLUGIN::metric)
      .toSet()
  }

  private fun getNotBundledPlugins(): Set<MetricEvent> {
    return PluginManagerCore.plugins.asSequence()
      .filter { !it.isBundled && !getPluginInfoByDescriptor(it).isSafeToReport() }
      // This will be validated by list of plugin ids from server
      // and ONLY provided ids will be reported
      .map { UNSAFE_PLUGIN.metric(it.pluginId.idString, it.isEnabled) }
      .toSet()
  }
}