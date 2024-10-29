// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector.performInstalledTabSearch
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector.performMarketplaceSearch
import com.intellij.ide.plugins.marketplace.statistics.collectors.PluginManagerFUSCollector
import com.intellij.ide.plugins.marketplace.statistics.collectors.PluginManagerMPCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.SignatureVerificationResult
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

  // Plugin manager search session identifier which is unique within one IDE session
  private val sessionId = AtomicInteger(-1)
  // Search index within one plugin manager search session. The order corresponds to the order of query updates
  private val searchIndex = AtomicInteger(0)

  private val installedPluginInSession = AtomicBoolean(false)

  @JvmStatic
  fun sessionStarted(): Int {
    searchIndex.set(0)
    installedPluginInSession.set(false)
    return sessionId.getAndIncrement()
  }

  /**
   * Event that happens on search restart intention, before receiving the list with plugins.
   * Unlike with [performMarketplaceSearch] and [performInstalledTabSearch],
   * the order of these events corresponds to the order of query updates.
   */
  @JvmStatic
  fun updateAndGetSearchIndex(): Int {
    // If we perform search after installing a plugin, we consider this as a new search session:
    if (installedPluginInSession.compareAndSet(true, false)) sessionStarted()
    return searchIndex.getAndIncrement()
  }

  fun performMarketplaceSearch(
    project: Project?,
    query: SearchQueryParser.Marketplace,
    results: List<IdeaPluginDescriptor>,
    searchIndex: Int,
    pluginToScore: Map<IdeaPluginDescriptor, Double>? = null
  ) {
    mpCollector.performMarketplaceSearch(project, query, results, searchIndex, sessionId.get(), pluginToScore)
  }

  @JvmStatic
  fun performInstalledTabSearch(
    project: Project?,
    query: SearchQueryParser.Installed,
    results: List<IdeaPluginDescriptor>,
    searchIndex: Int,
    pluginToScore: Map<IdeaPluginDescriptor, Double>? = null
  ) {
    mpCollector.performInstalledTabSearch(project, query, results, searchIndex, sessionId.get(), pluginToScore)
  }

  fun searchReset() {
    mpCollector.searchReset(sessionId.get())
  }

  @JvmStatic
  fun pluginCardOpened(descriptor: IdeaPluginDescriptor, group: PluginsGroup?) {
    fusCollector.pluginCardOpened(descriptor, group, sessionId.get())
    mpCollector.pluginCardOpened(descriptor, group, sessionId.get())
  }

  @JvmStatic
  fun thirdPartyAcceptanceCheck(result: DialogAcceptanceResultEnum) {
    fusCollector.thirdPartyAcceptanceCheck(result, sessionId.get())
    mpCollector.thirdPartyAcceptanceCheck(result, sessionId.get())
  }

  @JvmStatic
  fun pluginsStateChanged(
    descriptors: Collection<IdeaPluginDescriptor>,
    enable: Boolean,
    project: Project? = null,
  ) {
    fusCollector.pluginsStateChanged(descriptors, enable, project, sessionId.get())
    mpCollector.pluginsStateChanged(descriptors, enable, project, sessionId.get())
  }

  @JvmStatic
  fun pluginRemoved(pluginId: PluginId) {
    fusCollector.pluginRemoved(pluginId, sessionId.get())
    mpCollector.pluginRemoved(pluginId, sessionId.get())
  }

  @JvmStatic
  fun pluginInstallationStarted(
    descriptor: IdeaPluginDescriptor,
    source: InstallationSourceEnum,
    previousVersion: String? = null
  ) {
    fusCollector.pluginInstallationStarted(descriptor, source, sessionId.get(), previousVersion)
    mpCollector.pluginInstallationStarted(descriptor, source, sessionId.get(), previousVersion)
  }

  @JvmStatic
  fun pluginInstallationFinished(descriptor: IdeaPluginDescriptor) {
    installedPluginInSession.set(true)
    fusCollector.pluginInstallationFinished(descriptor, sessionId.get())
    mpCollector.pluginInstallationFinished(descriptor, sessionId.get())
  }

  fun signatureCheckResult(descriptor: IdeaPluginDescriptor, result: SignatureVerificationResult) {
    fusCollector.signatureCheckResult(descriptor, result, sessionId.get())
    mpCollector.signatureCheckResult(descriptor, result, sessionId.get())
  }

  fun signatureWarningShown(descriptor: IdeaPluginDescriptor, result: DialogAcceptanceResultEnum) {
    fusCollector.signatureWarningShown(descriptor, result, sessionId.get())
    mpCollector.signatureWarningShown(descriptor, result, sessionId.get())
  }
}