// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel

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
          ConfigurableWrapper.createConfigurables(MLCodeCompletionConfigurableEP.EP_NAME).forEach {
            appendDslConfigurable(it)
          }
        }
      }
  }
}
