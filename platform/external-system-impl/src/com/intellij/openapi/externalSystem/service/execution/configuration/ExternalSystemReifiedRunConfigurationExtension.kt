// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration

abstract class ExternalSystemReifiedRunConfigurationExtension<C : ExternalSystemRunConfiguration>(
  private val runConfigurationClass: Class<C>
) : ExternalSystemRunConfigurationExtension() {

  abstract fun SettingsFragmentsContainer<C>.configureFragments(configuration: C)

  override fun isApplicableFor(configuration: ExternalSystemRunConfiguration): Boolean {
    return runConfigurationClass.isInstance(configuration)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <P : ExternalSystemRunConfiguration> createFragments(configuration: P): List<SettingsEditorFragment<P, *>> {
    return SettingsFragmentsContainer.fragments<C> {
      configureFragments(configuration as C)
    } as List<SettingsEditorFragment<P, *>>
  }
}