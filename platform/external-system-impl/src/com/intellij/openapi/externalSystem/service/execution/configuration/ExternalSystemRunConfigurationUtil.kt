// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.ui.*
import com.intellij.ide.macro.MacrosDialog
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineField
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineInfo
import com.intellij.openapi.externalSystem.service.ui.distribution.DistributionComboBox
import com.intellij.openapi.externalSystem.service.ui.distribution.DistributionInfo
import com.intellij.openapi.externalSystem.service.ui.distribution.DistributionsInfo
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JComponent


fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addBeforeRunFragment(buildTaskKey: Key<*>) =
  add(createBeforeRunFragment(buildTaskKey))

fun <C : RunConfigurationBase<*>> createBeforeRunFragment(buildTaskKey: Key<*>): BeforeRunFragment<C> {
  val parentDisposable = Disposer.newDisposable()
  val beforeRunComponent = BeforeRunComponent(parentDisposable)
  val beforeRunFragment = BeforeRunFragment.createBeforeRun<C>(beforeRunComponent, buildTaskKey)
  Disposer.register(beforeRunFragment, parentDisposable)
  return beforeRunFragment
}

fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addSettingsTag(
  id: String,
  @Nls name: String,
  @Nls group: String,
  @Nls hint: String?,
  getter: (C) -> Boolean,
  setter: (C, Boolean) -> Unit,
  menuPosition: Int
) = add(createSettingsTag(id, name, group, hint, getter, setter, menuPosition))

@Suppress("UNCHECKED_CAST")
fun <C : RunConfigurationBase<*>> createSettingsTag(
  id: String,
  @Nls name: String,
  @Nls group: String,
  @Nls hint: String?,
  getter: (C) -> Boolean,
  setter: (C, Boolean) -> Unit,
  menuPosition: Int
): SettingsEditorFragment<C, *> =
  RunConfigurationEditorFragment.createSettingsTag<C>(
    id,
    name,
    group,
    { getter(it.configuration as C) },
    { it, v -> setter(it.configuration as C, v) },
    menuPosition
  ).apply {
    actionHint = hint
  }

fun <C : ExternalSystemRunConfiguration> SettingsFragmentsContainer<C>.addCommandLineFragment(
  project: Project,
  commandLineInfo: CommandLineInfo
) = addCommandLineFragment(
  project,
  commandLineInfo,
  { settings.commandLine },
  { settings.commandLine = it }
)

fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addCommandLineFragment(
  project: Project,
  commandLineInfo: CommandLineInfo,
  getCommandLine: C.() -> String,
  setCommandLine: C.(String) -> Unit
) = add(createCommandLineFragment(project, commandLineInfo, getCommandLine, setCommandLine))

fun <C : RunConfigurationBase<*>> createCommandLineFragment(
  project: Project,
  commandLineInfo: CommandLineInfo,
  getCommandLine: C.() -> String,
  setCommandLine: C.(String) -> Unit
): SettingsEditorFragment<C, CommandLineField> {
  val commandLineField = CommandLineField(project, commandLineInfo).apply {
    CommonParameterFragments.setMonospaced(this)
    FragmentedSettingsUtil.setupPlaceholderVisibility(this)
  }
  return SettingsEditorFragment<C, CommandLineField>(
    "external.system.command.line.fragment",
    commandLineInfo.settingsName,
    commandLineInfo.settingsGroup,
    commandLineField,
    commandLineInfo.settingsPriority,
    SettingsEditorFragmentType.COMMAND_LINE,
    { it, c -> c.commandLine = it.getCommandLine() },
    { it, c -> it.setCommandLine(c.commandLine) },
    { true }
  ).apply {
    isCanBeHidden = false
    isRemovable = false
    setHint(commandLineInfo.settingsHint)
    actionHint = commandLineInfo.settingsActionHint
  }
}

fun <C : ExternalSystemRunConfiguration> SettingsFragmentsContainer<C>.addWorkingDirectoryFragment(
  project: Project,
  workingDirectoryInfo: WorkingDirectoryInfo
) = addWorkingDirectoryFragment(
  project,
  workingDirectoryInfo,
  { settings.externalProjectPath ?: "" },
  { settings.externalProjectPath = it }
)

fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addWorkingDirectoryFragment(
  project: Project,
  workingDirectoryInfo: WorkingDirectoryInfo,
  getWorkingDirectory: C.() -> String,
  setWorkingDirectory: C.(String) -> Unit
) = add(createWorkingDirectoryFragment(project, workingDirectoryInfo, getWorkingDirectory, setWorkingDirectory))

fun <C : RunConfigurationBase<*>> createWorkingDirectoryFragment(
  project: Project,
  projectPathInfo: WorkingDirectoryInfo,
  getWorkingDirectory: C.() -> String,
  setWorkingDirectory: C.(String) -> Unit
): SettingsEditorFragment<C, LabeledComponent<WorkingDirectoryField>> {
  val workingDirectoryField = WorkingDirectoryField(project, projectPathInfo).apply {
    CommonParameterFragments.setMonospaced(this)
    FragmentedSettingsUtil.setupPlaceholderVisibility(this)
  }
  return SettingsEditorFragment<C, LabeledComponent<WorkingDirectoryField>>(
    "external.system.project.path.fragment",
    projectPathInfo.settingsName,
    projectPathInfo.settingsGroup,
    LabeledComponent.create(workingDirectoryField, projectPathInfo.settingsLabel, BorderLayout.WEST),
    projectPathInfo.settingsPriority,
    SettingsEditorFragmentType.EDITOR,
    { it, c -> it.getWorkingDirectory().let { p -> if (p.isNotBlank()) c.component.workingDirectory = p } },
    { it, c -> it.setWorkingDirectory(FileUtil.toCanonicalPath(c.component.workingDirectory)) },
    { true }
  ).apply {
    isCanBeHidden = false
    isRemovable = false
    setValidation(workingDirectoryField) {
      if (it.workingDirectory.isBlank()) {
        error(projectPathInfo.settingsEmptyError)
      }
      else {
        null
      }
    }
  }
}

fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addDistributionFragment(
  project: Project,
  distributionsInfo: DistributionsInfo,
  getDistribution: C.() -> DistributionInfo,
  setDistribution: C.(DistributionInfo) -> Unit,
  validate: ValidationInfoBuilder.(DistributionInfo) -> ValidationInfo?
) = add(createDistributionFragment(project, distributionsInfo, getDistribution, setDistribution, validate))

fun <C : RunConfigurationBase<*>> createDistributionFragment(
  project: Project,
  distributionsInfo: DistributionsInfo,
  getDistribution: C.() -> DistributionInfo,
  setDistribution: C.(DistributionInfo) -> Unit,
  validate: ValidationInfoBuilder.(DistributionInfo) -> ValidationInfo?
): SettingsEditorFragment<C, DistributionComboBox> {
  val comboBox = DistributionComboBox(project, distributionsInfo).apply {
    CommonParameterFragments.setMonospaced(this)
  }
  return SettingsEditorFragment<C, DistributionComboBox>(
    "external.system.distribution.fragment",
    distributionsInfo.settingsName,
    distributionsInfo.settingsGroup,
    comboBox,
    distributionsInfo.settingsPriority,
    SettingsEditorFragmentType.COMMAND_LINE,
    { it, c -> c.selectedDistribution = it.getDistribution() },
    { it, c -> it.setDistribution(c.selectedDistribution) },
    { true }
  ).apply {
    isCanBeHidden = false
    isRemovable = false
    setHint(distributionsInfo.settingsHint)
    actionHint = distributionsInfo.settingsActionHint
    setValidation {
      val validationInfoBuilder = ValidationInfoBuilder(comboBox)
      val selectedDistribution = comboBox.selectedDistribution
      val validationInfo = validationInfoBuilder.validate(selectedDistribution)
      listOfNotNull(validationInfo)
    }
  }
}


fun <C : ExternalSystemRunConfiguration> SettingsFragmentsContainer<C>.addVmOptionsFragment() =
  addVmOptionsFragment(
    { settings.vmOptions },
    { settings.vmOptions = it }
  )

fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addVmOptionsFragment(
  getVmOptions: C.() -> String,
  setVmOptions: C.(String) -> Unit
) = add(createVmOptionsFragment(getVmOptions, setVmOptions))

fun <C : RunConfigurationBase<*>> createVmOptionsFragment(
  getVmOptions: C.() -> String,
  setVmOptions: C.(String) -> Unit
): SettingsEditorFragment<C, LabeledComponent<RawCommandLineEditor>> {
  val vmOptions = RawCommandLineEditor().apply {
    CommonParameterFragments.setMonospaced(textField)
    MacrosDialog.addMacroSupport(editorField, MacrosDialog.Filters.ALL) { false }
    FragmentedSettingsUtil.setupPlaceholderVisibility(editorField)
  }
  val vmOptionsLabel = ExecutionBundle.message("run.configuration.java.vm.parameters.label")
  return SettingsEditorFragment<C, LabeledComponent<RawCommandLineEditor>>(
    "external.system.vm.parameters.fragment",
    ExecutionBundle.message("run.configuration.java.vm.parameters.name"),
    ExecutionBundle.message("group.java.options"),
    LabeledComponent.create(vmOptions, vmOptionsLabel, BorderLayout.WEST),
    { it, c -> c.component.text = it.getVmOptions() },
    { it, c -> it.setVmOptions(if (c.isVisible) c.component.text else "") },
    { it.getVmOptions().isNotBlank() }
  ).apply {
    isCanBeHidden = true
    isRemovable = true
    setHint(ExecutionBundle.message("run.configuration.java.vm.parameters.hint"))
    actionHint = ExecutionBundle.message("specify.vm.options.for.running.the.application")
  }
}


fun <C : ExternalSystemRunConfiguration> SettingsFragmentsContainer<C>.addEnvironmentFragment() =
  addEnvironmentFragment(
    { settings.env },
    { settings.env = it },
    { settings.isPassParentEnvs },
    { settings.isPassParentEnvs = it }
  )

fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addEnvironmentFragment(
  getEnvs: C.() -> Map<String, String>,
  setEnvs: C.(Map<String, String>) -> Unit,
  isPassParentEnvs: C.() -> Boolean,
  setPassParentEnvs: C.(Boolean) -> Unit
) = add(createEnvironmentFragment(getEnvs, setEnvs, isPassParentEnvs, setPassParentEnvs))

fun <C : RunConfigurationBase<*>> createEnvironmentFragment(
  getEnvs: C.() -> Map<String, String>,
  setEnvs: C.(Map<String, String>) -> Unit,
  isPassParentEnvs: C.() -> Boolean,
  setPassParentEnvs: C.(Boolean) -> Unit
): SettingsEditorFragment<C, EnvironmentVariablesComponent> {
  val environmentVariablesComponent = EnvironmentVariablesComponent().apply {
    labelLocation = BorderLayout.WEST
    CommonParameterFragments.setMonospaced(component.textField)
  }
  val fragment = SettingsEditorFragment<C, EnvironmentVariablesComponent>(
    "environmentVariables",
    ExecutionBundle.message("environment.variables.fragment.name"),
    ExecutionBundle.message("group.operating.system"),
    environmentVariablesComponent,
    { it, c ->
      c.envs = getEnvs(it)
      c.isPassParentEnvs = isPassParentEnvs(it)
    },
    { it, c ->
      setEnvs(it, if (c.isVisible) c.envs else emptyMap())
      setPassParentEnvs(it, if (c.isVisible) c.isPassParentEnvs else true)
    },
    { true }
  ).apply {
    isCanBeHidden = true
    isRemovable = true
    setHint(ExecutionBundle.message("environment.variables.fragment.hint"))
    actionHint = ExecutionBundle.message("set.custom.environment.variables.for.the.process")
  }
  return fragment
}

fun <C : JComponent> SettingsEditorFragment<*, *>.setValidation(
  component: C,
  validate: ValidationInfoBuilder.(C) -> ValidationInfo?
) {
  setValidation {
    val validationInfoBuilder = ValidationInfoBuilder(component)
    val validationInfo = validationInfoBuilder.validate(component)
    listOfNotNull(validationInfo)
  }
}
