// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.diagnostic.logging.LogsGroupFragment
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ui.*
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration

class ExternalSystemRunConfigurationFragmentedEditor(
  runConfiguration: ExternalSystemRunConfiguration
) : RunConfigurationFragmentedEditor<ExternalSystemRunConfiguration>(
  runConfiguration,
  ExternalSystemRunConfigurationExtensionManager.instance
) {
  override fun createRunFragments(): List<SettingsEditorFragment<ExternalSystemRunConfiguration, *>> {
    return ArrayList<SettingsEditorFragment<ExternalSystemRunConfiguration, *>>().apply {
      add(CommonParameterFragments.createRunHeader())
      addAll(BeforeRunFragment.createGroup())
      add(CommonTags.parallelRun())
      add(CommonParameterFragments.createEnvParameters())
      add(createVmOptions(ExternalSystemRunConfiguration::getVmOptions, ExternalSystemRunConfiguration::setVmOptions))
      add(LogsGroupFragment())
    }
  }
}