// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.enums.PluginsGroupType
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.SignatureVerificationResult
import com.intellij.ide.plugins.marketplace.statistics.features.*
import com.intellij.ide.plugins.newui.PluginsGroup
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.mp.MP_RECORDER_ID
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project

internal object PluginManagerUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = EVENT_GROUP

  private val EVENT_GROUP = EventLogGroup("plugin.manager", 8, MP_RECORDER_ID)
  private val PLUGINS_GROUP_TYPE = EventFields.Enum<PluginsGroupType>("group")
  private val ENABLE_DISABLE_ACTION = EventFields.Enum<PluginEnabledState>("enabled_state")
  private val ACCEPTANCE_RESULT = EventFields.Enum<DialogAcceptanceResultEnum>("acceptance_result")
  private val PLUGIN_SOURCE = EventFields.Enum<InstallationSourceEnum>("source")
  private val PREVIOUS_VERSION = PluginVersionEventField("previous_version")
  private val SIGNATURE_CHECK_RESULT = EventFields.Enum<SignatureVerificationResult>("signature_check_result")

  private val PLUGIN_CARD_OPENED = EVENT_GROUP.registerEvent(
    "plugin.search.card.opened", EventFields.PluginInfo, PLUGINS_GROUP_TYPE, EventFields.Int("index")
  )
  private val THIRD_PARTY_ACCEPTANCE_CHECK = EVENT_GROUP.registerEvent("plugin.install.third.party.check", ACCEPTANCE_RESULT)
  private val PLUGIN_SIGNATURE_WARNING = EVENT_GROUP.registerEvent(
    "plugin.signature.warning.shown", EventFields.PluginInfo, ACCEPTANCE_RESULT
  )
  private val PLUGIN_SIGNATURE_CHECK_RESULT = EVENT_GROUP.registerEvent(
    "plugin.signature.check.result", EventFields.PluginInfo, SIGNATURE_CHECK_RESULT
  )
  private val PLUGIN_STATE_CHANGED = EVENT_GROUP.registerEvent(
    "plugin.state.changed", EventFields.PluginInfo, ENABLE_DISABLE_ACTION
  )
  private val PLUGIN_INSTALLATION_STARTED = EVENT_GROUP.registerEvent(
    "plugin.installation.started", PLUGIN_SOURCE, EventFields.PluginInfo, PREVIOUS_VERSION
  )
  private val PLUGIN_INSTALLATION_FINISHED = EVENT_GROUP.registerEvent("plugin.installation.finished", EventFields.PluginInfo)
  private val PLUGIN_REMOVED = EVENT_GROUP.registerEvent("plugin.was.removed", EventFields.PluginInfo)

  // Search
  private val USER_QUERY_FEATURES_DATA_KEY = ObjectEventField(
    "userQueryFeatures", *PluginManagerUserQueryFeatureProvider.getFeaturesDefinition().toTypedArray()
  )
  private val MARKETPLACE_SEARCH_FEATURES_DATA_KEY = ObjectEventField(
    "marketplaceSearchFeatures", *PluginManagerMarketplaceSearchFeatureProvider.getFeaturesDefinition().toTypedArray()
  )
  private val LOCAL_SEARCH_FEATURES_DATA_KEY = ObjectEventField(
    "localSearchFeatures", *PluginManagerLocalSearchFeatureProvider.getFeaturesDefinition().toTypedArray()
  )
  private val SEARCH_RESULTS_FEATURES_DATA_KEY = ObjectEventField(
    "resultsFeatures", *PluginManagerSearchResultsFeatureProvider.getFeaturesDefinition().toTypedArray()
  )

  private val MARKETPLACE_TAB_SEARCH_PERFORMED = EVENT_GROUP.registerVarargEvent(
    "marketplace.tab.search", USER_QUERY_FEATURES_DATA_KEY, MARKETPLACE_SEARCH_FEATURES_DATA_KEY, SEARCH_RESULTS_FEATURES_DATA_KEY
  )

  private val INSTALLED_TAB_SEARCH_PERFORMED = EVENT_GROUP.registerVarargEvent(
    "installed.tab.search", USER_QUERY_FEATURES_DATA_KEY, LOCAL_SEARCH_FEATURES_DATA_KEY, SEARCH_RESULTS_FEATURES_DATA_KEY
  )

  private val SEARCH_RESET = EVENT_GROUP.registerEvent("search.reset")

  @JvmStatic
  fun performMarketplaceSearch(project: Project?, query: SearchQueryParser.Marketplace, results: List<IdeaPluginDescriptor>) {
    MARKETPLACE_TAB_SEARCH_PERFORMED.getIfInitializedOrNull()?.log(project) {
      add(USER_QUERY_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerUserQueryFeatureProvider().getSearchStateFeatures(query.searchQuery)
      )))
      add(MARKETPLACE_SEARCH_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerMarketplaceSearchFeatureProvider().getSearchStateFeatures(query)
      )))
      add(SEARCH_RESULTS_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerSearchResultsFeatureProvider().getSearchStateFeatures(query.searchQuery, results)
      )))
    }
  }

  @JvmStatic
  fun performInstalledTabSearch(project: Project?, query: SearchQueryParser.Installed, results: List<IdeaPluginDescriptor>) {
    INSTALLED_TAB_SEARCH_PERFORMED.getIfInitializedOrNull()?.log(project) {
      add(USER_QUERY_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerUserQueryFeatureProvider().getSearchStateFeatures(query.searchQuery)
      )))
      add(LOCAL_SEARCH_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerLocalSearchFeatureProvider().getSearchStateFeatures(query)
      )))
      add(SEARCH_RESULTS_FEATURES_DATA_KEY.with(ObjectEventData(
        PluginManagerSearchResultsFeatureProvider().getSearchStateFeatures(query.searchQuery, results)
      )))
    }
  }

  @JvmStatic
  fun searchReset() {
    SEARCH_RESET.log()
  }

  @JvmStatic
  fun pluginCardOpened(descriptor: IdeaPluginDescriptor, group: PluginsGroup?): Unit? = group?.let {
    PLUGIN_CARD_OPENED.log(getPluginInfoByDescriptor(descriptor), it.type, it.getPluginIndex(descriptor.pluginId))
  }

  @JvmStatic
  fun thirdPartyAcceptanceCheck(result: DialogAcceptanceResultEnum) {
    THIRD_PARTY_ACCEPTANCE_CHECK.getIfInitializedOrNull()?.log(result)
  }

  @JvmStatic
  fun pluginsStateChanged(
    descriptors: Collection<IdeaPluginDescriptor>,
    enable: Boolean,
    project: Project? = null,
  ) {
    PLUGIN_STATE_CHANGED.getIfInitializedOrNull()?.let { event ->
      descriptors.forEach { descriptor ->
        event.log(
          project,
          getPluginInfoByDescriptor(descriptor),
          PluginEnabledState.getState(enable),
        )
      }
    }
  }

  @JvmStatic
  fun pluginRemoved(pluginId: PluginId): Unit? = PLUGIN_REMOVED.getIfInitializedOrNull()?.log(getPluginInfoById(pluginId))

  @JvmStatic
  fun pluginInstallationStarted(
    descriptor: IdeaPluginDescriptor,
    source: InstallationSourceEnum,
    previousVersion: String? = null
  ) {
    val pluginInfo = getPluginInfoByDescriptor(descriptor)
    PLUGIN_INSTALLATION_STARTED.getIfInitializedOrNull()?.log(source, pluginInfo,
                                                              if (pluginInfo.isSafeToReport()) previousVersion else null)
  }

  @JvmStatic
  fun pluginInstallationFinished(descriptor: IdeaPluginDescriptor): Unit? = getPluginInfoByDescriptor(descriptor).let {
    PLUGIN_INSTALLATION_FINISHED.getIfInitializedOrNull()?.log(it)
  }

  fun signatureCheckResult(descriptor: IdeaPluginDescriptor, result: SignatureVerificationResult): Unit? =
    PLUGIN_SIGNATURE_CHECK_RESULT.getIfInitializedOrNull()?.log(getPluginInfoByDescriptor(descriptor), result)

  fun signatureWarningShown(descriptor: IdeaPluginDescriptor, result: DialogAcceptanceResultEnum): Unit? =
    PLUGIN_SIGNATURE_WARNING.getIfInitializedOrNull()?.log(getPluginInfoByDescriptor(descriptor), result)
}

private data class PluginVersionEventField(override val name: String) : PrimitiveEventField<String?>() {
  override val validationRule: List<String>
    get() = listOf("{util#plugin_version}")

  override fun addData(fuData: FeatureUsageData, value: String?) {
    if (!value.isNullOrEmpty()) {
      fuData.addData(name, value)
    }
  }
}

// We don't want to log actions when app did not initialize yet (e.g. migration process)
private fun <T : BaseEventId> T.getIfInitializedOrNull(): T? = if (ApplicationManager.getApplication() == null) null else this