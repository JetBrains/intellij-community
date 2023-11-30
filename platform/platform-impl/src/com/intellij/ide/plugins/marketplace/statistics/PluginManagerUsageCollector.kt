// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics

import com.intellij.ide.plugins.IdeaPluginDescriptor
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

/*
  Collects plugin manager usage statistics:
    - Data about search requests on the Marketplace and Installed tabs and search resets actions will be collected
      by [PluginManagerMPCollector].
    - Data about opening plugin cards, installing/removing plugins and plugin state changes will be collected by
      both [PluginManagerMPCollector] and [PluginManagerFUSCollector] for backward compatibility.
 */

@ApiStatus.Internal
internal object PluginManagerUsageCollector {
  private val fusCollector = PluginManagerFUSCollector()
  private val mpCollector = PluginManagerMPCollector()

  @JvmStatic
  fun performMarketplaceSearch(
    project: Project?,
    query: SearchQueryParser.Marketplace,
    results: List<IdeaPluginDescriptor>
  ) = mpCollector.performMarketplaceSearch(project, query, results)

  @JvmStatic
  fun performInstalledTabSearch(
    project: Project?,
    query: SearchQueryParser.Installed,
    results: List<IdeaPluginDescriptor>
  ) = mpCollector.performInstalledTabSearch(project, query, results)

  @JvmStatic
  fun searchReset() = mpCollector.searchReset()

  @JvmStatic
  fun pluginCardOpened(descriptor: IdeaPluginDescriptor, group: PluginsGroup?) {
    fusCollector.pluginCardOpened(descriptor, group)
    mpCollector.pluginCardOpened(descriptor, group)
  }

  @JvmStatic
  fun thirdPartyAcceptanceCheck(result: DialogAcceptanceResultEnum) {
    fusCollector.thirdPartyAcceptanceCheck(result)
    mpCollector.thirdPartyAcceptanceCheck(result)
  }

  @JvmStatic
  fun pluginsStateChanged(
    descriptors: Collection<IdeaPluginDescriptor>,
    enable: Boolean,
    project: Project? = null,
  ) {
    fusCollector.pluginsStateChanged(descriptors, enable, project)
    mpCollector.pluginsStateChanged(descriptors, enable, project)
  }

  @JvmStatic
  fun pluginRemoved(pluginId: PluginId) {
    fusCollector.pluginRemoved(pluginId)
    mpCollector.pluginRemoved(pluginId)
  }

  @JvmStatic
  fun pluginInstallationStarted(
    descriptor: IdeaPluginDescriptor,
    source: InstallationSourceEnum,
    previousVersion: String? = null
  ) {
    fusCollector.pluginInstallationStarted(descriptor, source, previousVersion)
    mpCollector.pluginInstallationStarted(descriptor, source, previousVersion)
  }

  @JvmStatic
  fun pluginInstallationFinished(descriptor: IdeaPluginDescriptor) {
    fusCollector.pluginInstallationFinished(descriptor)
    mpCollector.pluginInstallationFinished(descriptor)
  }

  fun signatureCheckResult(descriptor: IdeaPluginDescriptor, result: SignatureVerificationResult) {
    fusCollector.signatureCheckResult(descriptor, result)
    mpCollector.signatureCheckResult(descriptor, result)
  }

  fun signatureWarningShown(descriptor: IdeaPluginDescriptor, result: DialogAcceptanceResultEnum) {
    fusCollector.signatureWarningShown(descriptor, result)
    mpCollector.signatureWarningShown(descriptor, result)
  }
}