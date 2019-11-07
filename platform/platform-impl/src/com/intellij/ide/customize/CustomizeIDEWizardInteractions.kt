// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.application.ApplicationManager

object CustomizeIDEWizardInteractions {
  var skippedOnPage = -1
  val interactions = mutableListOf<CustomizeIDEWizardInteraction>()

  @JvmOverloads
  fun record(type: CustomizeIDEWizardInteractionType, pluginId: String? = null, groupId: String? = null) {
    interactions.add(CustomizeIDEWizardInteraction(type, pluginId, groupId))
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

data class CustomizeIDEWizardInteraction(val type: CustomizeIDEWizardInteractionType, val pluginId: String?, val groupId: String?)

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
        interaction.pluginId?.let { data.addData("plugin_id", it) }
        interaction.groupId?.let { data.addData("group", it) }
        FUCounterUsageLogger.getInstance().logEvent("customize.wizard", interaction.type.toString(), data)
      }
    }
  }
}