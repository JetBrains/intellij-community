// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import javax.swing.Action

interface PluginManagerCustomizer {
  fun getInstallButonCustomizationModel(
    pluginModelFacade: PluginModelFacade,
    pluginToInstallModel: PluginUiModel,
    modalityState: ModalityState,
  ): OptionsButonCustomizationModel?

  fun getDisableButtonCustomizationModel(
    pluginModelFacade: PluginModelFacade,
    pluginModel: PluginUiModel,
    modalityState: ModalityState,
  ): OptionsButonCustomizationModel

  @Nls
  fun getAdditionalTitleText(pluginModel: PluginUiModel): String?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PluginManagerCustomizer> = ExtensionPointName("com.intellij.pluginManagerCustomizer")
  }
}

data class OptionsButonCustomizationModel(
  val additionalActions: List<AnAction>,
  val isVisible: Boolean = true,
  val mainAction: Action? = null,
  @param:NlsSafe val text: String? = null,
)