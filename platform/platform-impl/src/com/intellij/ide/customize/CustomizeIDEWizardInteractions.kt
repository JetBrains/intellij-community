// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.internal.statistic.FeaturedPluginsInfoProvider
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptorWithFeaturedPlugins
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicReference

object CustomizeIDEWizardInteractions {
  /**
   * Featured plugins group which are suggested in IDE Customization Wizard.
   */
  val featuredPluginGroups = AtomicReference<PluginGroups>()

  var skippedOnPage = -1
  val interactions = mutableListOf<CustomizeIDEWizardInteraction>()

  @JvmOverloads
  fun record(type: CustomizeIDEWizardInteractionType, pluginDescriptor: PluginDescriptor? = null, groupId: String? = null) {
    interactions.add(CustomizeIDEWizardInteraction(type, System.currentTimeMillis(), pluginDescriptor, groupId))
  }
}

enum class CustomizeIDEWizardInteractionType {
  WizardDisplayed,
  UIThemeChanged,
  DesktopEntryCreated,
  LauncherScriptCreated,
  BundledPluginGroupDisabled,
  BundledPluginGroupEnabled,
  BundledPluginGroupCustomized,
  FeaturedPluginInstalled
}

data class CustomizeIDEWizardInteraction(
  val type: CustomizeIDEWizardInteractionType,
  val timestamp: Long,
  val pluginDescriptor: PluginDescriptor?,
  val groupId: String?
)

internal class CustomizeIDEWizardCollectorActivity : ApplicationInitializedListener {
  override fun componentsInitialized() {
    if (CustomizeIDEWizardInteractions.interactions.isEmpty()) {
      return
    }

    ForkJoinPool.commonPool().execute {
      if (CustomizeIDEWizardInteractions.skippedOnPage != -1) {
        CustomizeWizardCollector.logRemainingPagesSkipped(CustomizeIDEWizardInteractions.skippedOnPage)
      }

      val featuredPluginsProvider = CustomizeIDEWizardFeaturedPluginsProvider(CustomizeIDEWizardInteractions.featuredPluginGroups.get())
      for (interaction in CustomizeIDEWizardInteractions.interactions) {
        val pluginInfo = if (interaction.pluginDescriptor != null)
          getPluginInfoByDescriptorWithFeaturedPlugins(interaction.pluginDescriptor, featuredPluginsProvider)
        else
          null

        CustomizeWizardCollector.logEvent(interaction.type, interaction.timestamp, pluginInfo, interaction.groupId)
      }
    }
  }
}

private class CustomizeIDEWizardFeaturedPluginsProvider(private val pluginGroups: PluginGroups?) : FeaturedPluginsInfoProvider {
  private val validatedPlugins: Set<PluginId> by lazy {
    if (pluginGroups != null) {
      val fromRepository = pluginGroups.pluginsFromRepository
      if (fromRepository.isNotEmpty()) {
        return@lazy fromRepository.map { it.pluginId }.toSet()
      }
    }
    return@lazy emptySet()
  }

  override fun getFeaturedPluginsFromMarketplace(): Set<PluginId> = validatedPlugins
}