// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.codeInsight.inline.completion.options.InlineCompletionConfigurable
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MLCodeCompletionConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable? {
    val configurable = MLCodeCompletionConfigurable()
    if (configurable.configurables.isNotEmpty()) {
      return configurable
    }

    return null
  }

  private class MLCodeCompletionConfigurable : BoundCompositeConfigurable<UnnamedConfigurable>(
    ApplicationBundle.message("group.completion.ml")
  ), WithEpDependencies {

    override fun getDependencies(): Collection<BaseExtensionPointName<*>> =
      listOf(MLCodeCompletionConfigurableEP.EP_NAME)

    override fun createConfigurables(): List<UnnamedConfigurable> =
      ConfigurableWrapper.createConfigurables(MLCodeCompletionConfigurableEP.EP_NAME)

    override fun createPanel(): DialogPanel =
      panel {
        group(displayName) {
          linkToInlineCompletionSettings()
          ConfigurableWrapper.createConfigurables(MLCodeCompletionConfigurableEP.EP_NAME).forEach {
            appendDslConfigurable(it)
          }
        }
      }

    private fun Panel.linkToInlineCompletionSettings() {
      row {
        text(ApplicationBundle.message("text.go.to.inline.completion.settings")) { _ ->
          DataManager.getInstance().dataContextFromFocusAsync.then {
            Settings.KEY.getData(it)?.let { settings ->
              settings.find(InlineCompletionConfigurable::class.java)?.let { configurable ->
                settings.select(configurable)
              }
            }
          }
        }
      }
    }
  }
}
