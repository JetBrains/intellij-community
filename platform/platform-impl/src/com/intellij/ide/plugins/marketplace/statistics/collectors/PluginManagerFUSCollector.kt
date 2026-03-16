// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.collectors

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.PluginsGroupType
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.SignatureVerificationResult
import com.intellij.ide.plugins.marketplace.statistics.fields.PluginVersionEventField
import com.intellij.ide.plugins.newui.PluginsGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BaseEventId
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

internal const val PM_FUS_GROUP_ID = "plugin.manager"
internal const val PM_FUS_GROUP_VERSION = 9
private val EVENT_GROUP = EventLogGroup(PM_FUS_GROUP_ID, PM_FUS_GROUP_VERSION)

@ApiStatus.Internal
open class PluginManagerFUSCollector : CounterUsagesCollector() {
  override fun getGroup() = EVENT_GROUP

  @Suppress("PropertyName")
  protected val PLUGIN_MANAGER_SESSION_ID = IntEventField("sessionId")
  @Suppress("PropertyName")
  protected val PLUGIN_MANAGER_SEARCH_INDEX = IntEventField("searchIndex")

  private val PLUGINS_GROUP_TYPE = EventFields.Enum<PluginsGroupType>("group")
  private val ENABLE_DISABLE_ACTION = EventFields.Enum<PluginEnabledState>("enabled_state")
  private val ACCEPTANCE_RESULT = EventFields.Enum<DialogAcceptanceResultEnum>("acceptance_result")
  private val PLUGIN_SOURCE = EventFields.Enum<InstallationSourceEnum>("source")
  private val PREVIOUS_VERSION = PluginVersionEventField("previous_version")
  private val SIGNATURE_CHECK_RESULT = EventFields.Enum<SignatureVerificationResult>("signature_check_result")
  private val PLUGIN_LIST_INDEX = EventFields.Int("index")

  private val PLUGIN_CARD_OPENED = group.registerVarargEvent(
    "plugin.search.card.opened", EventFields.PluginInfo, PLUGINS_GROUP_TYPE,
    PLUGIN_LIST_INDEX, PLUGIN_MANAGER_SESSION_ID
  )
  private val THIRD_PARTY_ACCEPTANCE_CHECK = group.registerEvent("plugin.install.third.party.check",
                                                                 ACCEPTANCE_RESULT, PLUGIN_MANAGER_SESSION_ID)
  private val PLUGIN_SIGNATURE_WARNING = group.registerEvent(
    "plugin.signature.warning.shown", EventFields.PluginInfo, ACCEPTANCE_RESULT, PLUGIN_MANAGER_SESSION_ID
  )
  private val PLUGIN_SIGNATURE_CHECK_RESULT = group.registerEvent(
    "plugin.signature.check.result", EventFields.PluginInfo, SIGNATURE_CHECK_RESULT, PLUGIN_MANAGER_SESSION_ID
  )
  private val PLUGIN_STATE_CHANGED = group.registerEvent(
    "plugin.state.changed", EventFields.PluginInfo, ENABLE_DISABLE_ACTION, PLUGIN_MANAGER_SESSION_ID
  )
  private val PLUGIN_INSTALLATION_STARTED = group.registerVarargEvent(
    "plugin.installation.started", PLUGIN_SOURCE, EventFields.PluginInfo, PREVIOUS_VERSION, PLUGIN_MANAGER_SESSION_ID
  )
  private val PLUGIN_INSTALLATION_FINISHED = group.registerEvent("plugin.installation.finished", EventFields.PluginInfo, PLUGIN_MANAGER_SESSION_ID)
  private val PLUGIN_REMOVED = group.registerEvent("plugin.was.removed", EventFields.PluginInfo, PLUGIN_MANAGER_SESSION_ID)

  fun pluginCardOpened(descriptor: IdeaPluginDescriptor, group: PluginsGroup?, sessionId: Int): Unit? = group?.let {
    PLUGIN_CARD_OPENED.log(
      EventFields.PluginInfo.with(getPluginInfoByDescriptor(descriptor)),
      PLUGINS_GROUP_TYPE.with(it.type),
      EventFields.Int("index").with(it.getPluginIndex(descriptor.pluginId)),
      PLUGIN_MANAGER_SESSION_ID.with(sessionId)
    )
  }

  fun thirdPartyAcceptanceCheck(result: DialogAcceptanceResultEnum, sessionId: Int) {
    THIRD_PARTY_ACCEPTANCE_CHECK.getIfInitializedOrNull()?.log(result, sessionId)
  }

  fun pluginsStateChanged(
    descriptors: Collection<IdeaPluginDescriptor>,
    enable: Boolean,
    project: Project? = null,
    sessionId: Int
  ) {
    PLUGIN_STATE_CHANGED.getIfInitializedOrNull()?.let { event ->
      descriptors.forEach { descriptor ->
        event.log(project, getPluginInfoByDescriptor(descriptor), PluginEnabledState.getState(enable), sessionId)
      }
    }
  }

  fun pluginRemoved(pluginId: PluginId, sessionId: Int): Unit? = PLUGIN_REMOVED.getIfInitializedOrNull()
    ?.log(getPluginInfoById(pluginId), sessionId)

  fun pluginInstallationStarted(
    descriptor: IdeaPluginDescriptor,
    source: InstallationSourceEnum,
    sessionId: Int,
    previousVersion: String? = null,
  ) {
    val pluginInfo = getPluginInfoByDescriptor(descriptor)
    PLUGIN_INSTALLATION_STARTED.getIfInitializedOrNull()?.log(
      PLUGIN_SOURCE.with(source), EventFields.PluginInfo.with(pluginInfo),
      PREVIOUS_VERSION.with(pluginInfo to previousVersion), PLUGIN_MANAGER_SESSION_ID.with(sessionId))
  }

  fun pluginInstallationFinished(descriptor: IdeaPluginDescriptor, sessionId: Int): Unit? = getPluginInfoByDescriptor(descriptor).let {
    PLUGIN_INSTALLATION_FINISHED.getIfInitializedOrNull()?.log(it, sessionId)
  }

  fun signatureCheckResult(descriptor: IdeaPluginDescriptor, result: SignatureVerificationResult, sessionId: Int): Unit? =
    PLUGIN_SIGNATURE_CHECK_RESULT.getIfInitializedOrNull()?.log(getPluginInfoByDescriptor(descriptor), result, sessionId)

  fun signatureWarningShown(descriptor: IdeaPluginDescriptor, result: DialogAcceptanceResultEnum, sessionId: Int): Unit? =
    PLUGIN_SIGNATURE_WARNING.getIfInitializedOrNull()?.log(getPluginInfoByDescriptor(descriptor), result, sessionId)

  // We don't want to log actions when app did not initialize yet (e.g. migration process)
  protected fun <T : BaseEventId> T.getIfInitializedOrNull(): T? = if (ApplicationManager.getApplication() == null) null else this
}