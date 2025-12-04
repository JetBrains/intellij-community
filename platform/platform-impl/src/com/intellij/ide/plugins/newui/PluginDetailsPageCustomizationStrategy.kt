// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

// Interface that allows to modify PluginDetailsPageComponent. Needed because the component is used in many dialogs (Plugin Advertiser, Plugin Manager, Plugin Updater, etc.).
@ApiStatus.Internal
interface PluginDetailsPageCustomizationStrategy {
  @NlsSafe
  fun getAdditionalText(pluginModel: PluginUiModel): String?
  fun isAdditionalTextVisible(pluginModel: PluginUiModel, isMarketPlace: Boolean): Boolean
}

@ApiStatus.Internal
object DefaultPluginDetailsPageCustomizationStrategy : PluginDetailsPageCustomizationStrategy {
  override fun getAdditionalText(pluginModel: PluginUiModel): String? {
    return PluginManagerCustomizer.getInstance()?.getAdditionalTitleText(pluginModel)
  }

  override fun isAdditionalTextVisible(pluginModel: PluginUiModel, isMarketPlace: Boolean): Boolean {
    return true
  }
}

@ApiStatus.Internal
object UpdateDialogPluginDetailsPageCustomizationStrategy : PluginDetailsPageCustomizationStrategy {
  override fun getAdditionalText(pluginModel: PluginUiModel): String? {
    return PluginManagerCustomizer.getInstance()?.getUpdateSourceText(pluginModel)
  }

  override fun isAdditionalTextVisible(pluginModel: PluginUiModel, isMarketPlace: Boolean): Boolean {
    return true
  }
}
