// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace.statistics

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.enums.PluginsGroupType
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.SignatureVerificationResult
import com.intellij.ide.plugins.newui.PluginsGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.BaseEventId
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project


class PluginManagerUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = EVENT_GROUP

  companion object {
    private val EVENT_GROUP = EventLogGroup("plugin.manager", 3)
    private val PLUGINS_GROUP_TYPE = EventFields.Enum<PluginsGroupType>("group")
    private val ENABLE_DISABLE_ACTION = EventFields.Enum<PluginEnableDisableAction>("states") { it.name }
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

    @JvmStatic
    fun pluginCardOpened(descriptor: IdeaPluginDescriptor, group: PluginsGroup?) = group?.let {
      PLUGIN_CARD_OPENED.log(getPluginInfoByDescriptor(descriptor), it.type, it.getPluginIndex(descriptor.pluginId))
    }

    @JvmStatic
    fun thirdPartyAcceptanceCheck(result: DialogAcceptanceResultEnum) = THIRD_PARTY_ACCEPTANCE_CHECK.getIfInitializedOrNull()?.log(result)

    @JvmStatic
    fun pluginsStateChanged(
      descriptors: Collection<IdeaPluginDescriptor>,
      action: PluginEnableDisableAction,
      project: Project? = null,
    ) {
      descriptors.asSequence().map {
        getPluginInfoByDescriptor(it)
      }.forEach {
        PLUGIN_STATE_CHANGED.getIfInitializedOrNull()?.log(project, it, action)
      }
    }

    @JvmStatic
    fun pluginRemoved(pluginId: PluginId) = PLUGIN_REMOVED.getIfInitializedOrNull()?.log(getPluginInfoById(pluginId))

    @JvmStatic
    fun pluginInstallationStarted(
      descriptor: IdeaPluginDescriptor,
      source: InstallationSourceEnum,
      previousVersion: String? = null
    ) {
      val pluginInfo = getPluginInfoByDescriptor(descriptor)
      PLUGIN_INSTALLATION_STARTED.getIfInitializedOrNull()?.log(source, pluginInfo, if (pluginInfo.isSafeToReport()) previousVersion else null)
    }

    @JvmStatic
    fun pluginInstallationFinished(descriptor: IdeaPluginDescriptor) = getPluginInfoByDescriptor(descriptor).let {
      PLUGIN_INSTALLATION_FINISHED.getIfInitializedOrNull()?.log(it)
    }

    fun signatureCheckResult(descriptor: IdeaPluginDescriptor, result: SignatureVerificationResult) =
      PLUGIN_SIGNATURE_CHECK_RESULT.getIfInitializedOrNull()?.log(getPluginInfoByDescriptor(descriptor), result)

    fun signatureWarningShown(descriptor: IdeaPluginDescriptor, result: DialogAcceptanceResultEnum) =
      PLUGIN_SIGNATURE_WARNING.getIfInitializedOrNull()?.log(getPluginInfoByDescriptor(descriptor), result)
  }

  private data class PluginVersionEventField(override val name: String): PrimitiveEventField<String?>() {
    override val validationRule: List<String>
      get() = listOf("{util#plugin_version}")

    override fun addData(fuData: FeatureUsageData, value: String?) {
      if (!value.isNullOrEmpty()) {
        fuData.addData(name, value)
      }
    }
  }
}

// We don't want to log actions when app did not initialized yet (e.g. migration process)
private fun <T: BaseEventId> T.getIfInitializedOrNull(): T? = if (ApplicationManager.getApplication() == null) null else this