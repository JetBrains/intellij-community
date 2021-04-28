// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace.statistics

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.SignatureVerificationResult
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project


class PluginManagerUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = EVENT_GROUP

  companion object {
    private val EVENT_GROUP = EventLogGroup("plugin.manager", 1)
    private val ENABLE_DISABLE_ACTION = EventFields.Enum<PluginEnableDisableAction>("states") { it.name }
    private val ACCEPTANCE_RESULT = EventFields.Enum<DialogAcceptanceResultEnum>("acceptance_result")
    private val PLUGIN_SOURCE = EventFields.Enum<InstallationSourceEnum>("source")
    private val PREVIOUS_VERSION = PluginVersionEventField("previous_version")
    private val SIGNATURE_CHECK_RESULT = EventFields.Enum<SignatureVerificationResult>("signature_check_result")

    private val THIRD_PARTY_ACCEPTANCE_CHECK = EVENT_GROUP.registerEvent("plugin.install.third.party.check", ACCEPTANCE_RESULT)
    private val PLUGIN_SIGNATURE_WARNING = EVENT_GROUP.registerEvent(
      "plugin.signature.warning.shown",
      EventFields.PluginInfo,
      ACCEPTANCE_RESULT
    )
    private val PLUGIN_SIGNATURE_CHECK_RESULT = EVENT_GROUP.registerEvent("plugin.signature.check.result",
      EventFields.PluginInfo,
      SIGNATURE_CHECK_RESULT
    )
    private val PLUGIN_STATE_CHANGED = EVENT_GROUP.registerEvent("plugin.state.changed", EventFields.PluginInfo, ENABLE_DISABLE_ACTION
    )
    private val PLUGIN_INSTALLATION_STARTED = EVENT_GROUP.registerEvent(
      "plugin.installation.started", PLUGIN_SOURCE, EventFields.PluginInfo, PREVIOUS_VERSION
    )
    private val PLUGIN_INSTALLATION_FINISHED = EVENT_GROUP.registerEvent("plugin.installation.finished", EventFields.PluginInfo)
    private val PLUGIN_REMOVED = EVENT_GROUP.registerEvent("plugin.was.removed", EventFields.PluginInfo)

    @JvmStatic
    fun thirdPartyAcceptanceCheck(result: DialogAcceptanceResultEnum) = THIRD_PARTY_ACCEPTANCE_CHECK.log(result)

    @JvmStatic
    fun pluginsStateChanged(project: Project?, pluginIds: List<PluginId>, action: PluginEnableDisableAction) = pluginIds
      .forEach { PLUGIN_STATE_CHANGED.log(project, it.pluginInfoIfSafeToReport(), action) }

    @JvmStatic
    fun pluginRemoved(pluginId: PluginId) = PLUGIN_REMOVED.log(pluginId.pluginInfoIfSafeToReport())

    @JvmStatic
    fun pluginInstallationStarted(
      descriptor: IdeaPluginDescriptor,
      source: InstallationSourceEnum,
      previousVersion: String? = null
    ) = descriptor.pluginInfoIfSafeToReport()?.let {
      PLUGIN_INSTALLATION_STARTED.log(source, it, previousVersion)
    } ?: PLUGIN_INSTALLATION_STARTED.log(source, null, null)

    @JvmStatic
    fun pluginInstallationFinished(descriptor: IdeaPluginDescriptor) = descriptor.pluginInfoIfSafeToReport().let {
      PLUGIN_INSTALLATION_FINISHED.log(it)
    }

    fun signatureCheckResult(descriptor: IdeaPluginDescriptor, result: SignatureVerificationResult) = PLUGIN_SIGNATURE_CHECK_RESULT.log(
      descriptor.pluginInfoIfSafeToReport(), result
    )

    fun signatureWarningShown(descriptor: IdeaPluginDescriptor, result: DialogAcceptanceResultEnum) = PLUGIN_SIGNATURE_WARNING.log(
      descriptor.pluginInfoIfSafeToReport(), result
    )

    private fun PluginId.pluginInfoIfSafeToReport() = getPluginInfoById(this).takeIf { it.isSafeToReport() }
    private fun IdeaPluginDescriptor.pluginInfoIfSafeToReport() = getPluginInfoByDescriptor(this).takeIf { it.isSafeToReport() }
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