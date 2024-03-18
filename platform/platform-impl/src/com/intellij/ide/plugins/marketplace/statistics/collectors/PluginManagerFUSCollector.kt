// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.collectors

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.enums.PluginsGroupType
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.SignatureVerificationResult
import com.intellij.ide.plugins.marketplace.statistics.fields.PluginVersionEventField
import com.intellij.ide.plugins.newui.PluginsGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BaseEventId
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

internal const val PM_FUS_GROUP_ID = "plugin.manager"
internal const val PM_FUS_GROUP_VERSION = 8
private val EVENT_GROUP = EventLogGroup(PM_FUS_GROUP_ID, PM_FUS_GROUP_VERSION)

@ApiStatus.Internal
open class PluginManagerFUSCollector : CounterUsagesCollector() {
  override fun getGroup() = EVENT_GROUP

  private val PLUGINS_GROUP_TYPE = EventFields.Enum<PluginsGroupType>("group")
  private val ENABLE_DISABLE_ACTION = EventFields.Enum<PluginEnabledState>("enabled_state")
  private val ACCEPTANCE_RESULT = EventFields.Enum<DialogAcceptanceResultEnum>("acceptance_result")
  private val PLUGIN_SOURCE = EventFields.Enum<InstallationSourceEnum>("source")
  private val PREVIOUS_VERSION = PluginVersionEventField("previous_version")
  private val SIGNATURE_CHECK_RESULT = EventFields.Enum<SignatureVerificationResult>("signature_check_result")

  private val PLUGIN_CARD_OPENED = group.registerEvent(
    "plugin.search.card.opened", EventFields.PluginInfo, PLUGINS_GROUP_TYPE, EventFields.Int("index")
  )
  private val THIRD_PARTY_ACCEPTANCE_CHECK = group.registerEvent("plugin.install.third.party.check",
                                                                 ACCEPTANCE_RESULT)
  private val PLUGIN_SIGNATURE_WARNING = group.registerEvent(
    "plugin.signature.warning.shown", EventFields.PluginInfo, ACCEPTANCE_RESULT
  )
  private val PLUGIN_SIGNATURE_CHECK_RESULT = group.registerEvent(
    "plugin.signature.check.result", EventFields.PluginInfo, SIGNATURE_CHECK_RESULT
  )
  private val PLUGIN_STATE_CHANGED = group.registerEvent(
    "plugin.state.changed", EventFields.PluginInfo, ENABLE_DISABLE_ACTION
  )
  private val PLUGIN_INSTALLATION_STARTED = group.registerEvent(
    "plugin.installation.started", PLUGIN_SOURCE, EventFields.PluginInfo, PREVIOUS_VERSION
  )
  private val PLUGIN_INSTALLATION_FINISHED = group.registerEvent("plugin.installation.finished", EventFields.PluginInfo)
  private val PLUGIN_REMOVED = group.registerEvent("plugin.was.removed", EventFields.PluginInfo)

  fun pluginCardOpened(descriptor: IdeaPluginDescriptor, group: PluginsGroup?): Unit? = group?.let {
    PLUGIN_CARD_OPENED.log(getPluginInfoByDescriptor(descriptor), it.type, it.getPluginIndex(descriptor.pluginId))
  }

  fun thirdPartyAcceptanceCheck(result: DialogAcceptanceResultEnum) {
    THIRD_PARTY_ACCEPTANCE_CHECK.getIfInitializedOrNull()?.log(result)
  }

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

  fun pluginRemoved(pluginId: PluginId): Unit? = PLUGIN_REMOVED.getIfInitializedOrNull()?.log(getPluginInfoById(pluginId))

  fun pluginInstallationStarted(
    descriptor: IdeaPluginDescriptor,
    source: InstallationSourceEnum,
    previousVersion: String? = null
  ) {
    val pluginInfo = getPluginInfoByDescriptor(descriptor)
    PLUGIN_INSTALLATION_STARTED.getIfInitializedOrNull()?.log(source, pluginInfo, pluginInfo to previousVersion)
  }

  fun pluginInstallationFinished(descriptor: IdeaPluginDescriptor): Unit? = getPluginInfoByDescriptor(descriptor).let {
    PLUGIN_INSTALLATION_FINISHED.getIfInitializedOrNull()?.log(it)
  }

  fun signatureCheckResult(descriptor: IdeaPluginDescriptor, result: SignatureVerificationResult): Unit? =
    PLUGIN_SIGNATURE_CHECK_RESULT.getIfInitializedOrNull()?.log(getPluginInfoByDescriptor(descriptor), result)

  fun signatureWarningShown(descriptor: IdeaPluginDescriptor, result: DialogAcceptanceResultEnum): Unit? =
    PLUGIN_SIGNATURE_WARNING.getIfInitializedOrNull()?.log(getPluginInfoByDescriptor(descriptor), result)

  // We don't want to log actions when app did not initialize yet (e.g. migration process)
  protected fun <T : BaseEventId> T.getIfInitializedOrNull(): T? = if (ApplicationManager.getApplication() == null) null else this
}