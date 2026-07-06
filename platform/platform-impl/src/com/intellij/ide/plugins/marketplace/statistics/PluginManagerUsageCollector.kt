// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.marketplace.statistics.collectors.PluginManagerFUSCollector
import com.intellij.ide.plugins.marketplace.statistics.collectors.PluginManagerMPCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerButtonType
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerManageAction
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerOpenSourceEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerSide
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerTab
import com.intellij.ide.plugins.marketplace.statistics.enums.SignatureVerificationResult
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginsGroup
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/*
  Collects plugin manager usage statistics:
    - Data about search requests on the Marketplace and Installed tabs and search resets actions will be collected
      by [PluginManagerMPCollector].
    - Data about opening plugin cards, installing/removing plugins and plugin state changes will be collected by
      both [PluginManagerMPCollector] and [PluginManagerFUSCollector] for backward compatibility.
 */
@ApiStatus.Internal
object PluginManagerUsageCollector {
  private val fusCollector = PluginManagerFUSCollector()
  private val mpCollector = PluginManagerMPCollector()

  // Plugin manager UI session identifier which is unique within one IDE session
  private val sessionId = AtomicInteger(-1)
  // Plugin manager search session identifier which is unique within one IDE session
  private val searchSessionId = AtomicInteger(-1)
  // Search index within one plugin manager search session. The order corresponds to the order of query updates
  private val searchIndex = AtomicInteger(0)

  private val installedPluginInSession = AtomicBoolean(false)

  @JvmStatic
  fun logSessionStarted(openSource: PluginManagerOpenSourceEnum = PluginManagerOpenSourceEnum.OTHER): Int {
    val newSessionId = sessionId.incrementAndGet()
    val newSearchSessionId = startNewSearchSession()
    fusCollector.sessionStarted(openSource, newSessionId, newSearchSessionId)
    mpCollector.sessionStarted(openSource, newSessionId, newSearchSessionId)
    return newSessionId
  }

  @JvmStatic
  fun tabSelected(tab: PluginManagerTab) {
    fusCollector.tabSelected(tab, sessionId.get(), searchSessionId.get())
    mpCollector.tabSelected(tab, sessionId.get(), searchSessionId.get())
  }

  @JvmStatic
  fun manageActionInvoked(action: PluginManagerManageAction) {
    fusCollector.manageActionInvoked(action, sessionId.get(), searchSessionId.get())
    mpCollector.manageActionInvoked(action, sessionId.get(), searchSessionId.get())
  }

  private fun startNewSearchSession(): Int {
    searchIndex.set(0)
    installedPluginInSession.set(false)
    return searchSessionId.incrementAndGet()
  }

  /**
   * Event that happens on search restart intention, before receiving the list with plugins.
   * Unlike with [performMarketplaceSearch] and [performInstalledTabSearch],
   * the order of these events corresponds to the order of query updates.
   */
  @JvmStatic
  fun updateAndGetSearchIndex(): Int {
    // If we perform search after installing a plugin, we consider this as a new search session:
    if (installedPluginInSession.compareAndSet(true, false)) startNewSearchSession()
    return searchIndex.getAndIncrement()
  }

  fun performMarketplaceSearch(
    project: Project?,
    query: SearchQueryParser.Marketplace,
    results: List<PluginUiModel>,
    searchIndex: Int,
    pluginToScore: Map<PluginUiModel, Double>? = null
  ) {
    mpCollector.performMarketplaceSearch(project, query, results, searchIndex, sessionId.get(), searchSessionId.get(), pluginToScore)
  }

  @JvmStatic
  fun performInstalledTabSearch(
    project: Project?,
    query: SearchQueryParser.Installed,
    results: List<PluginUiModel>,
    searchIndex: Int,
    pluginToScore: Map<PluginUiModel, Double>? = null
  ) {
    mpCollector.performInstalledTabSearch(project, query, results, searchIndex, sessionId.get(), searchSessionId.get(), pluginToScore)
  }

  fun searchReset() {
    mpCollector.searchReset(sessionId.get(), searchSessionId.get())
  }

  @JvmStatic
  fun pluginCardOpened(descriptor: IdeaPluginDescriptor, group: PluginsGroup?) {
    fusCollector.pluginCardOpened(descriptor, group, sessionId.get(), searchSessionId.get())
    mpCollector.pluginCardOpened(descriptor, group, sessionId.get(), searchSessionId.get())
  }

  @JvmStatic
  fun thirdPartyAcceptanceCheck(result: DialogAcceptanceResultEnum) {
    fusCollector.thirdPartyAcceptanceCheck(result, sessionId.get(), searchSessionId.get())
    mpCollector.thirdPartyAcceptanceCheck(result, sessionId.get(), searchSessionId.get())
  }

  @JvmStatic
  fun pluginsStateChanged(
    descriptors: Collection<IdeaPluginDescriptor>,
    enable: Boolean,
    project: Project? = null,
  ) {
    fusCollector.pluginsStateChanged(descriptors, enable, project, sessionId.get(), searchSessionId.get())
    mpCollector.pluginsStateChanged(descriptors, enable, project, sessionId.get(), searchSessionId.get())
  }

  @JvmStatic
  fun pluginRemoved(pluginId: PluginId) {
    fusCollector.pluginRemoved(pluginId, sessionId.get(), searchSessionId.get())
    mpCollector.pluginRemoved(pluginId, sessionId.get(), searchSessionId.get())
  }

  @JvmStatic
  fun pluginInstallButtonClicked(pluginId: PluginId, side: PluginManagerSide, buttonType: PluginManagerButtonType) {
    fusCollector.pluginInstallButtonClicked(pluginId, side, buttonType, sessionId.get(), searchSessionId.get())
    mpCollector.pluginInstallButtonClicked(pluginId, side, buttonType, sessionId.get(), searchSessionId.get())
  }

  @JvmStatic
  fun pluginUninstallButtonClicked(pluginId: PluginId, side: PluginManagerSide, buttonType: PluginManagerButtonType) {
    fusCollector.pluginUninstallButtonClicked(pluginId, side, buttonType, sessionId.get(), searchSessionId.get())
    mpCollector.pluginUninstallButtonClicked(pluginId, side, buttonType, sessionId.get(), searchSessionId.get())
  }

  @JvmStatic
  fun pluginInstallationStarted(
    descriptor: IdeaPluginDescriptor,
    source: InstallationSourceEnum,
    previousVersion: String? = null
  ) {
    fusCollector.pluginInstallationStarted(descriptor, source, sessionId.get(), searchSessionId.get(), previousVersion)
    mpCollector.pluginInstallationStarted(descriptor, source, sessionId.get(), searchSessionId.get(), previousVersion)
  }

  @JvmStatic
  fun pluginInstallationFinished(descriptor: IdeaPluginDescriptor) {
    installedPluginInSession.set(true)
    fusCollector.pluginInstallationFinished(descriptor, sessionId.get(), searchSessionId.get())
    mpCollector.pluginInstallationFinished(descriptor, sessionId.get(), searchSessionId.get())
  }

  fun signatureCheckResult(descriptor: IdeaPluginDescriptor, result: SignatureVerificationResult) {
    fusCollector.signatureCheckResult(descriptor, result, sessionId.get(), searchSessionId.get())
    mpCollector.signatureCheckResult(descriptor, result, sessionId.get(), searchSessionId.get())
  }

  fun signatureWarningShown(descriptor: IdeaPluginDescriptor, result: DialogAcceptanceResultEnum) {
    fusCollector.signatureWarningShown(descriptor, result, sessionId.get(), searchSessionId.get())
    mpCollector.signatureWarningShown(descriptor, result, sessionId.get(), searchSessionId.get())
  }
}
