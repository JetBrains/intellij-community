// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.ui.*
import com.intellij.ide.macro.MacrosDialog
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.ui.distribution.ExternalSystemDistributionComboBox
import com.intellij.openapi.externalSystem.service.ui.distribution.ExternalSystemDistributionInfo
import com.intellij.openapi.externalSystem.service.ui.distribution.ExternalSystemDistributionsInfo
import com.intellij.openapi.externalSystem.service.ui.project.path.ExternalSystemProjectPathField
import com.intellij.openapi.externalSystem.service.ui.project.path.ExternalSystemProjectPathInfo
import com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments.ExternalSystemTasksAndArgumentsField
import com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments.ExternalSystemTasksAndArgumentsInfo
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


fun <C : RunConfigurationBase<*>> createBeforeRun(buildTaskKey: Key<*>): BeforeRunFragment<C> {
  val parentDisposable = Disposer.newDisposable()
  val beforeRunComponent = BeforeRunComponent(parentDisposable)
  val beforeRunFragment = BeforeRunFragment.createBeforeRun<C>(beforeRunComponent, buildTaskKey)
  Disposer.register(beforeRunFragment, parentDisposable)
  return beforeRunFragment
}

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

fun <C : ExternalSystemRunConfiguration> createTasksAndArguments(
  project: Project,
  tasksAndArgumentsInfo: ExternalSystemTasksAndArgumentsInfo
): SettingsEditorFragment<C, ExternalSystemTasksAndArgumentsField> {
  return createTasksAndArguments(
    project,
    tasksAndArgumentsInfo,
    ExternalSystemRunConfiguration::getTasksAndArguments,
    ExternalSystemRunConfiguration::setTasksAndArguments
  )
}

fun <C : RunConfigurationBase<*>> createTasksAndArguments(
  project: Project,
  tasksAndArgumentsInfo: ExternalSystemTasksAndArgumentsInfo,
  getTasksAndArguments: C.() -> String,
  setTasksAndArguments: C.(String) -> Unit
): SettingsEditorFragment<C, ExternalSystemTasksAndArgumentsField> {
  val taskAndArgumentsField = ExternalSystemTasksAndArgumentsField(project, tasksAndArgumentsInfo).apply {
    CommonParameterFragments.setMonospaced(this)
    FragmentedSettingsUtil.setupPlaceholderVisibility(this)
  }
  return SettingsEditorFragment<C, ExternalSystemTasksAndArgumentsField>(
    "external.system.tasks.and.arguments.fragment",
    tasksAndArgumentsInfo.name,
    null,
    taskAndArgumentsField,
    100,
    { it, c -> c.tasksAndArguments = it.getTasksAndArguments() },
    { it, c -> it.setTasksAndArguments(c.tasksAndArguments) },
    { true }
  ).apply {
    isCanBeHidden = false
    isRemovable = false
    setHint(tasksAndArgumentsInfo.hint)
  }
}


fun <C : ExternalSystemRunConfiguration> createProjectPath(
  project: Project,
  projectPathInfo: ExternalSystemProjectPathInfo
): SettingsEditorFragment<C, LabeledComponent<ExternalSystemProjectPathField>> {
  return createProjectPath(
    project,
    projectPathInfo,
    ExternalSystemRunConfiguration::getExternalProjectPath,
    ExternalSystemRunConfiguration::setExternalProjectPath
  )
}

fun <C : RunConfigurationBase<*>> createProjectPath(
  project: Project,
  projectPathInfo: ExternalSystemProjectPathInfo,
  getProjectPath: C.() -> String?,
  setProjectPath: C.(String) -> Unit
): SettingsEditorFragment<C, LabeledComponent<ExternalSystemProjectPathField>> {
  val projectPathField = ExternalSystemProjectPathField(project, projectPathInfo).apply {
    CommonParameterFragments.setMonospaced(this)
    FragmentedSettingsUtil.setupPlaceholderVisibility(this)
  }
  return SettingsEditorFragment<C, LabeledComponent<ExternalSystemProjectPathField>>(
    "external.system.project.path.fragment",
    projectPathInfo.name,
    null,
    LabeledComponent.create(projectPathField, projectPathInfo.label, BorderLayout.WEST),
    -10,
    SettingsEditorFragmentType.EDITOR,
    { it, c -> it.getProjectPath()?.let { p -> c.component.projectPath = p } },
    { it, c -> it.setProjectPath(FileUtil.toCanonicalPath(c.component.projectPath)) },
    { true }
  ).apply {
    isCanBeHidden = false
    isRemovable = false
  }
}

fun <C : RunConfigurationBase<*>> createDistribution(
  project: Project,
  distributionsInfo: ExternalSystemDistributionsInfo,
  getDistribution: C.() -> ExternalSystemDistributionInfo?,
  setDistribution: C.(ExternalSystemDistributionInfo) -> Unit,
  validate: ValidationInfoBuilder.(ExternalSystemDistributionInfo) -> ValidationInfo?
): SettingsEditorFragment<C, ExternalSystemDistributionComboBox> {
  val comboBox = ExternalSystemDistributionComboBox(project, distributionsInfo).apply {
    CommonParameterFragments.setMonospaced(this)
  }
  return SettingsEditorFragment<C, ExternalSystemDistributionComboBox>(
    "external.system.distribution.fragment",
    distributionsInfo.name,
    null,
    comboBox,
    90,
    { it, c -> it.getDistribution()?.let { d -> c.selectedDistribution = d } },
    { it, c -> it.setDistribution(c.selectedDistribution) },
    { true }
  ).apply {
    isCanBeHidden = false
    isRemovable = false
    setHint(distributionsInfo.hint)
    setValidation {
      val validationInfoBuilder = ValidationInfoBuilder(comboBox)
      val selectedDistribution = comboBox.selectedDistribution
      val validationInfo = validationInfoBuilder.validate(selectedDistribution)
      listOfNotNull(validationInfo)
    }
  }
}

fun <C : RunConfigurationBase<*>> createVmOptions(
  getVmOptions: C.() -> String?,
  setVmOptions: C.(String?) -> Unit
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
    { it, c -> it.getVmOptions()?.let { o -> c.component.text = o } },
    { it, c -> it.setVmOptions(if (c.isVisible) c.component.text else null) },
    { !it.getVmOptions().isNullOrBlank() }
  ).apply {
    isCanBeHidden = true
    isRemovable = true
    setHint(ExecutionBundle.message("run.configuration.java.vm.parameters.hint"))
    actionHint = ExecutionBundle.message("specify.vm.options.for.running.the.application")
  }
}

fun <C : RunConfigurationBase<*>> createEnvParameters(
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
