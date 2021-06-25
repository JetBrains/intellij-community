// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.ui.*
import com.intellij.ide.macro.MacrosDialog
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineField
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineInfo
import com.intellij.openapi.externalSystem.service.ui.distribution.DistributionComboBox
import com.intellij.openapi.externalSystem.service.ui.distribution.DistributionInfo
import com.intellij.openapi.externalSystem.service.ui.distribution.DistributionsInfo
import com.intellij.openapi.externalSystem.service.ui.getModelPath
import com.intellij.openapi.externalSystem.service.ui.getUiPath
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryInfo
import com.intellij.openapi.externalSystem.service.ui.util.LabeledSettingsFragmentInfo
import com.intellij.openapi.externalSystem.service.ui.util.PathFragmentInfo
import com.intellij.openapi.externalSystem.service.ui.util.SettingsFragmentInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.TextAccessor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.textFieldWithBrowseButton
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.util.*
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

@Suppress("HardCodedStringLiteral")
inline fun <S, reified V : Enum<V>> SettingsFragmentsContainer<S>.addVariantTag(
  id: String,
  @Nls(capitalization = Nls.Capitalization.Sentence) name: String,
  @Nls(capitalization = Nls.Capitalization.Title) group: String?,
  crossinline getter: S.() -> V?,
  crossinline setter: S.(V?) -> Unit,
  crossinline getText: (V) -> String
): VariantTagFragment<S, V?> = add(VariantTagFragment.createFragment(
  id,
  name,
  group,
  { EnumSet.allOf(V::class.java).toTypedArray() },
  { it.getter() },
  { it, v -> it.setter(v) },
  { it.getter() != null }
)).apply {
  setVariantNameProvider { it?.let(getText) ?: "" }
  setDefaultVariant(null)
}

fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addTag(
  id: String,
  @Nls name: String,
  @Nls group: String,
  @Nls hint: String?,
  getter: C.() -> Boolean,
  setter: C.(Boolean) -> Unit
) = add(createTag(id, name, group, hint, getter, setter))

fun <C : RunConfigurationBase<*>> createTag(
  id: String,
  @Nls name: String,
  @Nls group: String,
  @Nls hint: String?,
  getter: C.() -> Boolean,
  setter: C.(Boolean) -> Unit
): SettingsEditorFragment<C, TagButton> =
  SettingsEditorFragment.createTag<C>(
    id,
    name,
    group,
    { it.getter() },
    { it, v -> it.setter(v) }
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
) = createSettingsEditorFragment<C, CommandLineField>(
  CommandLineField(project, commandLineInfo),
  commandLineInfo,
  { it, c -> c.commandLine = it.getCommandLine() },
  { it, c -> it.setCommandLine(c.commandLine) },
)

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
  workingDirectoryInfo: WorkingDirectoryInfo,
  getWorkingDirectory: C.() -> String,
  setWorkingDirectory: C.(String) -> Unit
) = createLabeledSettingsEditorFragment<C, WorkingDirectoryField>(
  WorkingDirectoryField(project, workingDirectoryInfo),
  workingDirectoryInfo,
  { it, c -> it.getWorkingDirectory().let { p -> if (p.isNotBlank()) c.workingDirectory = p } },
  { it, c -> it.setWorkingDirectory(c.workingDirectory) }
).apply {
  isRemovable = false
}.addValidation {
  if (it.getWorkingDirectory().isBlank()) {
    throw RuntimeConfigurationError(workingDirectoryInfo.emptyFieldError)
  }
}

fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addDistributionFragment(
  project: Project,
  distributionsInfo: DistributionsInfo,
  getDistribution: C.() -> DistributionInfo,
  setDistribution: C.(DistributionInfo) -> Unit
) = add(createDistributionFragment(project, distributionsInfo, getDistribution, setDistribution))

fun <C : RunConfigurationBase<*>> createDistributionFragment(
  project: Project,
  distributionsInfo: DistributionsInfo,
  getDistribution: C.() -> DistributionInfo,
  setDistribution: C.(DistributionInfo) -> Unit
) = createSettingsEditorFragment<C, DistributionComboBox>(
  DistributionComboBox(project, distributionsInfo),
  distributionsInfo,
  { it, c -> c.selectedDistribution = it.getDistribution() },
  { it, c -> it.setDistribution(c.selectedDistribution) },
)

fun <C : ExternalSystemRunConfiguration> SettingsFragmentsContainer<C>.addVmOptionsFragment() =
  addVmOptionsFragment(
    { settings.vmOptions },
    { settings.vmOptions = it }
  )

fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addVmOptionsFragment(
  getVmOptions: C.() -> String?,
  setVmOptions: C.(String?) -> Unit
) = add(createVmOptionsFragment(getVmOptions, setVmOptions))

fun <C : RunConfigurationBase<*>> createVmOptionsFragment(
  getVmOptions: C.() -> String?,
  setVmOptions: C.(String?) -> Unit
) = createLabeledTextSettingsEditorFragment(
  RawCommandLineEditor().apply {
    MacrosDialog.addMacroSupport(editorField, MacrosDialog.Filters.ALL) { false }
  },
  object : LabeledSettingsFragmentInfo {
    override val editorLabel: String = ExecutionBundle.message("run.configuration.java.vm.parameters.label")
    override val settingsId: String = "external.system.vm.options.fragment"
    override val settingsName: String = ExecutionBundle.message("run.configuration.java.vm.parameters.name")
    override val settingsGroup: String = ExecutionBundle.message("group.java.options")
    override val settingsHint: String = ExecutionBundle.message("run.configuration.java.vm.parameters.hint")
    override val settingsActionHint: String = ExecutionBundle.message("specify.vm.options.for.running.the.application")
  },
  getVmOptions,
  setVmOptions
)


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
) = createLabeledSettingsEditorFragment<C, EnvironmentVariablesTextFieldWithBrowseButton>(
  EnvironmentVariablesTextFieldWithBrowseButton(),
  object : LabeledSettingsFragmentInfo {
    override val editorLabel: String = ExecutionBundle.message("environment.variables.component.title")
    override val settingsId: String = "external.system.environment.variables.fragment"
    override val settingsName: String = ExecutionBundle.message("environment.variables.fragment.name")
    override val settingsGroup: String = ExecutionBundle.message("group.operating.system")
    override val settingsHint: String = ExecutionBundle.message("environment.variables.fragment.hint")
    override val settingsActionHint: String = ExecutionBundle.message("set.custom.environment.variables.for.the.process")
  },
  { it, c ->
    c.envs = getEnvs(it)
    c.isPassParentEnvs = isPassParentEnvs(it)
  },
  { it, c ->
    setEnvs(it, c.envs)
    setPassParentEnvs(it, c.isPassParentEnvs)
  }
)

fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addPathFragment(
  project: Project,
  pathFragmentInfo: PathFragmentInfo,
  getPath: C.() -> String?,
  setPath: C.(String?) -> Unit
) = add(createPathFragment(project, pathFragmentInfo, getPath, setPath))

fun <C : RunConfigurationBase<*>> createPathFragment(
  project: Project,
  pathFragmentInfo: PathFragmentInfo,
  getPath: C.() -> String?,
  setPath: C.(String?) -> Unit
) = createLabeledTextSettingsEditorFragment<C, TextFieldWithBrowseButton>(
  textFieldWithBrowseButton(
    project,
    pathFragmentInfo.fileChooserTitle,
    pathFragmentInfo.fileChooserDescription,
    ExtendableTextField(10).apply {
      val fileChooserMacroFilter = pathFragmentInfo.fileChooserMacroFilter
      if (fileChooserMacroFilter != null) {
        MacrosDialog.addMacroSupport(this, fileChooserMacroFilter) { false }
      }
    },
    pathFragmentInfo.fileChooserDescriptor
  ) { getUiPath(it.path) },
  pathFragmentInfo,
  { getPath()?.let(::getUiPath) },
  { setPath(it?.let(::getModelPath)) }
)

fun <S, C> createLabeledTextSettingsEditorFragment(
  component: C,
  info: LabeledSettingsFragmentInfo,
  getter: S.() -> String?,
  setter: S.(String?) -> Unit
) where C : JComponent, C : TextAccessor = createLabeledSettingsEditorFragment(
  component,
  info,
  TextAccessor::getText,
  TextAccessor::setText,
  getter,
  setter
)

fun <S, C : JComponent, V> createLabeledSettingsEditorFragment(
  component: C,
  info: LabeledSettingsFragmentInfo,
  getterC: C.() -> V,
  setterC: C.(V) -> Unit,
  getterS: S.() -> V?,
  setterS: S.(V?) -> Unit
) = createLabeledSettingsEditorFragment(
  component, info, getterC, setterC, { null }, getterS, setterS)

fun <S, C : JComponent, V> createLabeledSettingsEditorFragment(
  component: C,
  info: LabeledSettingsFragmentInfo,
  getterC: C.() -> V,
  setterC: C.(V) -> Unit,
  defaultS: S.() -> V?,
  getterS: S.() -> V?,
  setterS: S.(V?) -> Unit
): SettingsEditorFragment<S, LabeledComponent<C>> {
  val ref = Ref<SettingsEditorFragment<S, LabeledComponent<C>>>()
  return createLabeledSettingsEditorFragment<S, C>(
    component,
    info,
    { it, c -> (it.getterS() ?: it.defaultS())?.let { c.setterC(it) } },
    { it, c -> it.setterS(if (ref.get().isSelected) c.getterC() else null) },
    { it.getterS() != null }
  ).apply {
    isRemovable = true
    ref.set(this)
  }
}

fun <S, C : JComponent> createLabeledSettingsEditorFragment(
  component: C,
  settingsFragmentInfo: LabeledSettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
) = createLabeledSettingsEditorFragment(component, settingsFragmentInfo, reset, apply) { true }
  .apply { isRemovable = false }

fun <S, C : JComponent> createSettingsEditorFragment(
  component: C,
  settingsFragmentInfo: SettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
) = createSettingsEditorFragment(component, settingsFragmentInfo, reset, apply) { true }
  .apply { isRemovable = false }

fun <S, C : JComponent> createLabeledSettingsEditorFragment(
  component: C,
  info: LabeledSettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
  initialSelection: (S) -> Boolean
) = createSettingsEditorFragment(
  LabeledComponent.create(component, info.editorLabel, BorderLayout.WEST),
  info,
  { it, c -> reset(it, c.component) },
  { it, c -> apply(it, c.component) },
  initialSelection
)

fun <S, C : JComponent> createSettingsEditorFragment(
  component: C,
  settingsFragmentInfo: SettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
  initialSelection: (S) -> Boolean,
) = SettingsEditorFragment(
  settingsFragmentInfo.settingsId,
  settingsFragmentInfo.settingsName,
  settingsFragmentInfo.settingsGroup,
  component,
  settingsFragmentInfo.settingsPriority,
  settingsFragmentInfo.settingsType,
  reset,
  apply,
  initialSelection
).apply {
  setHint(settingsFragmentInfo.settingsHint)
  actionHint = settingsFragmentInfo.settingsActionHint

  val editorComponent = editorComponent
  CommonParameterFragments.setMonospaced(editorComponent)
  if (editorComponent is JBTextField) {
    FragmentedSettingsUtil.setupPlaceholderVisibility(editorComponent)
  }
}