// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.plugins

import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.updateSettings.impl.UpdateSettingsProvider

internal class PluginRepositoriesUsagesCollector : ApplicationUsagesCollector() {

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun getMetrics(): Set<MetricEvent> {
    val allCustomRepositories = RepositoryHelper.getCustomPluginRepositoryHosts()
    val storedPluginHosts = UpdateSettings.getInstance().storedPluginHosts
    val ideaPluginHosts = getFromProperty("idea.plugin.hosts")
    val fromProviders = UpdateSettingsProvider.getRepositoriesFromProviders()
    val fromCustomBuiltInRepo = getFromProperty("intellij.plugins.custom.built.in.repository.url")

    return setOf(
      CUSTOM_REPOSITORIES_EVENT.metric(NUMBER_OF_CUSTOM_REPOSITORIES_FIELD.with(allCustomRepositories.size),
                                       NUMBER_OF_USER_CONFIGURABLE_CUSTOM_REPOSITORIES_FIELD.with(storedPluginHosts.size),
                                       NUMBER_OF_REPOSITORIES_FROM_IDEA_PLUGIN_HOST_PROPERTY_FIELD.with(ideaPluginHosts.size),
                                       NUMBER_OF_REPOS_FROM_UPDATE_PROVIDERS_FIELD.with(fromProviders.size),
                                       NUMBER_OF_REPOS_FROM_BUILD_IN_PROPERTY.with(fromCustomBuiltInRepo.size))
    )
  }
}

private fun getFromProperty(propertyName: String): List<String> =
  System.getProperty(propertyName)?.split(";".toRegex())?.dropLastWhile { it.isEmpty() } ?: emptyList()

private val GROUP = EventLogGroup("plugin.repositories", 1)
private val NUMBER_OF_CUSTOM_REPOSITORIES_FIELD =
  RoundingIntEventField("number_of_custom_repositories",
                        "Number of all custom plugin repositories provided to IDE (with duplicates removed)")
private val NUMBER_OF_USER_CONFIGURABLE_CUSTOM_REPOSITORIES_FIELD =
  RoundingIntEventField("number_of_configurable_custom_repositories",
                        "Number of custom plugin repositories that are visible and configurable through a dialog in plugin settings")
private val NUMBER_OF_REPOSITORIES_FROM_IDEA_PLUGIN_HOST_PROPERTY_FIELD =
  RoundingIntEventField("number_of_repositories_from_idea_plugin_host_property",
                        "Number of custom plugin repositories that are configured via 'idea.plugin.hosts' property")
private val NUMBER_OF_REPOS_FROM_UPDATE_PROVIDERS_FIELD =
  RoundingIntEventField("number_of_repositories_from_update_settings_providers",
                        "Number of custom plugin repositories provided by UpdateSettingsProvider extension point")
private val NUMBER_OF_REPOS_FROM_BUILD_IN_PROPERTY =
  RoundingIntEventField("number_of_repositories_from_build_in_property",
                        "Number of custom plugin repositories that are configured via 'intellij.plugins.custom.built.in.repository.url' property")
private val CUSTOM_REPOSITORIES_EVENT = GROUP.registerVarargEvent("custom.repositories.configured",
                                                                  NUMBER_OF_CUSTOM_REPOSITORIES_FIELD,
                                                                  NUMBER_OF_USER_CONFIGURABLE_CUSTOM_REPOSITORIES_FIELD,
                                                                  NUMBER_OF_REPOSITORIES_FROM_IDEA_PLUGIN_HOST_PROPERTY_FIELD,
                                                                  NUMBER_OF_REPOS_FROM_UPDATE_PROVIDERS_FIELD,
                                                                  NUMBER_OF_REPOS_FROM_BUILD_IN_PROPERTY)

private class RoundingIntEventField(override val name: String, override val description: String) : PrimitiveEventField<Int>() {

  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, roundNumber(value))
  }

  private fun roundNumber(value: Int): Int {
    if (value < 5) return value
    return StatisticsUtil.roundToPowerOfTwo(value)
  }
}