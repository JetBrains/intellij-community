// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.SettingsEditorFragmentContainer
import com.intellij.openapi.options.SettingsEditor

/**
 * Adds cast and class restrictions on extendable run configuration.
 *
 * @see ExternalSystemRunConfigurationExtension
 */
abstract class ExternalSystemReifiedRunConfigurationExtension<C : ExternalSystemRunConfiguration>(
  private val runConfigurationClass: Class<C>
) : ExternalSystemRunConfigurationExtension() {

  /**
   * Creates and adds editor fragments into [this] fragments container.
   */
  abstract fun SettingsEditorFragmentContainer<C>.configureFragments(configuration: C)

  override fun isApplicableFor(configuration: ExternalSystemRunConfiguration): Boolean {
    return runConfigurationClass.isInstance(configuration)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <P : ExternalSystemRunConfiguration> createFragments(configuration: P): List<SettingsEditor<P>> {
    return SettingsEditorFragmentContainer.fragments {
      configureFragments(configuration as C)
    } as List<SettingsEditor<P>>
  }
}
