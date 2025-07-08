// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Action
import javax.swing.JComponent

@ApiStatus.Internal
interface PluginManagerCustomizer {
  fun initCustomizer(parentComponent: JComponent)

  fun getInstallButonCustomizationModel(
    pluginModelFacade: PluginModelFacade,
    pluginToInstallModel: PluginUiModel,
    modalityState: ModalityState,
  ): OptionsButonCustomizationModel?

  fun getDisableButtonCustomizationModel(
    pluginModelFacade: PluginModelFacade,
    pluginModel: PluginUiModel,
    modalityState: ModalityState,
  ): OptionsButonCustomizationModel?

  fun getUpdateButtonCustomizationModel(
    pluginModelFacade: PluginModelFacade,
    pluginModel: PluginUiModel,
    updateModel: PluginUiModel?,
    modalityState: ModalityState,
  ): UpdateButtonCustomizationModel?

  fun updateAfterModification(updateUi: () -> Unit)

  fun getExtraPluginsActions(): List<AnAction>

  fun onPluginDeleted(pluginModel: PluginUiModel, pluginSource: PluginSource)

  @Nls
  fun getAdditionalTitleText(pluginModel: PluginUiModel): String?

  fun ensurePluginStatesLoaded()

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PluginManagerCustomizer> = ExtensionPointName("com.intellij.pluginManagerCustomizer")

    @JvmStatic
    fun getInstance(): PluginManagerCustomizer? {
      if (Registry.`is`("reworked.plugin.manager.enabled", false)) {
        return EP_NAME.extensionList.firstOrNull()
      }
      return null
    }
  }
}

@ApiStatus.Internal
data class OptionsButonCustomizationModel(
  val additionalActions: List<AnAction>,
  val isVisible: Boolean = true,
  val mainAction: Action? = null,
  @param:NlsSafe val text: String? = null,
)

@ApiStatus.Internal
data class UpdateButtonCustomizationModel(
  val action: () -> Unit,
)