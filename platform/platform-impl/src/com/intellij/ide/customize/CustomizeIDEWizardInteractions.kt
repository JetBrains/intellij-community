// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginDescriptor

object CustomizeIDEWizardInteractions {
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

class CustomizeIDEWizardCollectorActivity : ApplicationInitializedListener {
  override fun componentsInitialized() {
    if (CustomizeIDEWizardInteractions.interactions.isEmpty()) return

    ApplicationManager.getApplication().executeOnPooledThread {
      if (CustomizeIDEWizardInteractions.skippedOnPage != -1) {
        FUCounterUsageLogger.getInstance().logEvent("customize.wizard", "remaining.pages.skipped",
                                                    FeatureUsageData().addData("page", CustomizeIDEWizardInteractions.skippedOnPage))
      }

      for (interaction in CustomizeIDEWizardInteractions.interactions) {
        val data = FeatureUsageData()
        data.addData("timestamp", interaction.timestamp)
        interaction.pluginDescriptor?.let { data.addPluginInfo(getPluginInfoByDescriptor(it)) }
        interaction.groupId?.let { data.addData("group", it) }
        FUCounterUsageLogger.getInstance().logEvent("customize.wizard", interaction.type.toString(), data)
      }
    }
  }
}