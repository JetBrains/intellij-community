// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.diagnostic.logging.LogsGroupFragment
import com.intellij.execution.ui.*
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.SettingsEditorFragmentContainer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ExternalSystemRunConfigurationFragmentedEditor(
  runConfiguration: ExternalSystemRunConfiguration
) : RunConfigurationFragmentedEditor<ExternalSystemRunConfiguration>(
  runConfiguration,
  ExternalSystemRunConfigurationExtensionManager.getInstance()
) {
  override fun createRunFragments(): List<SettingsEditorFragment<ExternalSystemRunConfiguration, *>> {
    return SettingsEditorFragmentContainer.fragments {
      add(CommonParameterFragments.createRunHeader())
      addAll(BeforeRunFragment.createGroup())
      add(CommonTags.parallelRun())
      addEnvironmentFragment()
      addVmOptionsFragment()
      add(LogsGroupFragment())
    }
  }
}