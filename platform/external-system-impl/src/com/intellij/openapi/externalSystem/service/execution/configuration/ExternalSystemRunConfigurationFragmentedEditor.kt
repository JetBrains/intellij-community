// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.diagnostic.logging.LogsGroupFragment
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ui.*
import com.intellij.ide.macro.MacrosDialog
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemProjectPathField
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.RawCommandLineEditor
import java.awt.BorderLayout

class ExternalSystemRunConfigurationFragmentedEditor(
  runConfiguration: ExternalSystemRunConfiguration
) : RunConfigurationFragmentedEditor<ExternalSystemRunConfiguration>(
  runConfiguration,
  ExternalSystemRunConfigurationExtensionManager.instance
) {

  override fun createRunFragments(): List<SettingsEditorFragment<ExternalSystemRunConfiguration, *>> {
    return ArrayList<SettingsEditorFragment<ExternalSystemRunConfiguration, *>>().apply {
      add(CommonParameterFragments.createHeader(ExecutionBundle.message("application.configuration.title.run")))
      addAll(BeforeRunFragment.createGroup())
      add(CommonTags.parallelRun())
      add(CommonParameterFragments.createEnvParameters())
      add(createVmOptions())
      add(LogsGroupFragment())
    }
  }

  private fun createVmOptions(): SettingsEditorFragment<ExternalSystemRunConfiguration, LabeledComponent<RawCommandLineEditor>> {
    val vmOptions = RawCommandLineEditor().apply {
      CommonParameterFragments.setMonospaced(textField)
      MacrosDialog.addMacroSupport(editorField, MacrosDialog.Filters.ALL) { false }
      FragmentedSettingsUtil.setupPlaceholderVisibility(editorField)
    }
    val vmOptionsLabel = ExecutionBundle.message("run.configuration.java.vm.parameters.label")
    return SettingsEditorFragment<ExternalSystemRunConfiguration, LabeledComponent<RawCommandLineEditor>>(
      "external.system.vm.parameters.fragment",
      ExecutionBundle.message("run.configuration.java.vm.parameters.name"),
      ExecutionBundle.message("group.java.options"),
      LabeledComponent.create(vmOptions, vmOptionsLabel, BorderLayout.WEST),
      { it, c -> c.component.text = it.vmOptions },
      { it, c -> it.vmOptions = if (c.isVisible) c.component.text else null },
      { !it.vmOptions.isNullOrBlank() }
    ).apply {
      isCanBeHidden = true
      isRemovable = true
      setHint(ExecutionBundle.message("run.configuration.java.vm.parameters.hint"))
      actionHint = ExecutionBundle.message("specify.vm.options.for.running.the.application")
    }
  }
}