// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij

import com.intellij.application.options.CodeStyleConfigurableWrapper
import com.intellij.application.options.CodeStyleSchemesConfigurable
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.application.options.codeStyle.CodeStyleSettingsPanelFactory
import com.intellij.application.options.codeStyle.NewCodeStyleSettingsPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class ConfigurableFactory {
  companion object {
    @JvmStatic
    fun getInstance(): ConfigurableFactory {
      return ApplicationManager.getApplication().getService(ConfigurableFactory::class.java)
    }
  }

  open fun createCodeStyleConfigurable(provider: CodeStyleSettingsProvider,
                                       codeStyleSchemesModel: CodeStyleSchemesModel,
                                       owner: CodeStyleSchemesConfigurable): CodeStyleConfigurableWrapper {
    val panelFactory = object : CodeStyleSettingsPanelFactory() {
      override fun createPanel(scheme: CodeStyleScheme): NewCodeStyleSettingsPanel {
        return NewCodeStyleSettingsPanel(
          provider.createConfigurable(scheme.codeStyleSettings, codeStyleSchemesModel.getCloneSettings(scheme)), codeStyleSchemesModel)
      }
    }
    if (provider is Configurable.Beta) {
      return BetaCodeStyleConfigurableWrapper(provider, panelFactory, owner)
    }
    return CodeStyleConfigurableWrapper(provider, panelFactory, owner)
  }
}

private class BetaCodeStyleConfigurableWrapper(
  provider: CodeStyleSettingsProvider,
  factory: CodeStyleSettingsPanelFactory,
  owner: CodeStyleSchemesConfigurable,
) : CodeStyleConfigurableWrapper(provider, factory, owner), Configurable.Beta
