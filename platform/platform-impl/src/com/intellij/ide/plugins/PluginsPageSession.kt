// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerOpenSourceEnum
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.JComponent

internal interface PluginsPageSession : Disposable {
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun getCenterComponent(controller: Configurable.TopComponentController): JComponent

  fun getComponent(): JComponent

  fun isMarketplaceTabShowing(): Boolean

  fun isInstalledTabShowing(): Boolean

  fun setInstallSource(source: FUSEventSource?)

  fun cancel()

  fun isModified(): Boolean

  fun scheduleApply()

  @Throws(ConfigurationException::class)
  fun apply()

  fun reset()

  fun selectAndEnable(descriptors: Set<IdeaPluginDescriptor>)

  fun select(pluginIds: Collection<PluginId>)

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun enableSearch(option: String?): Runnable?

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun enableSearch(option: String?, ignoreTagMarketplaceTab: Boolean): Runnable?

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun openMarketplaceTab(option: String)

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun openInstalledTab(option: String)
}

@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
internal fun createPluginsPageSession(
  searchQuery: String?,
  openSource: PluginManagerOpenSourceEnum,
): PluginsPageSession {
  return if (UnifiedPluginsPageFeature.isEnabled()) {
    UnifiedPluginsPageSession(searchQuery, openSource)
  }
  else {
    PluginManagerConfigurablePanel(searchQuery, openSource)
  }
}
