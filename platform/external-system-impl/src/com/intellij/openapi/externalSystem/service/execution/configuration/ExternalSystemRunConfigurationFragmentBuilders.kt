// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.ui.BeforeRunComponent
import com.intellij.execution.ui.BeforeRunFragment
import com.intellij.ide.macro.MacrosDialog
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.SettingsEditorFragmentContainer
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.addLabeledSettingsEditorFragment
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.addRemovableLabeledTextSettingsEditorFragment
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.addSettingsEditorFragment
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineField
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineInfo
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryInfo
import com.intellij.openapi.externalSystem.service.ui.util.AsyncDistributionsInfo
import com.intellij.openapi.externalSystem.service.ui.util.DistributionsInfo
import com.intellij.openapi.externalSystem.service.ui.util.LabeledSettingsFragmentInfo
import com.intellij.openapi.externalSystem.service.ui.util.PathFragmentInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.distribution.DistributionComboBox
import com.intellij.openapi.roots.ui.distribution.DistributionInfo
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.util.application
import com.intellij.util.text.nullize


fun <S : RunConfigurationBase<*>> SettingsEditorFragmentContainer<S>.addBeforeRunFragment(buildTaskKey: Key<*>) =
  add(createBeforeRunFragment(buildTaskKey))

fun <S : RunConfigurationBase<*>> createBeforeRunFragment(buildTaskKey: Key<*>): BeforeRunFragment<S> {
  val parentDisposable = Disposer.newDisposable()
  val beforeRunComponent = BeforeRunComponent(parentDisposable)
  val beforeRunFragment = BeforeRunFragment.createBeforeRun<S>(beforeRunComponent, buildTaskKey)
  Disposer.register(beforeRunFragment, parentDisposable)
  return beforeRunFragment
}

fun <S> SettingsEditorFragmentContainer<S>.addCommandLineFragment(
  project: Project,
  commandLineInfo: CommandLineInfo,
  getCommandLine: S.() -> String,
  setCommandLine: S.(String) -> Unit,
) = addSettingsEditorFragment(
  commandLineInfo,
  { CommandLineField(project, commandLineInfo, it) },
  { it, c -> c.commandLine = it.getCommandLine() },
  { it, c -> it.setCommandLine(c.commandLine) },
)

fun <S : ExternalSystemRunConfiguration> SettingsEditorFragmentContainer<S>.addWorkingDirectoryFragment(
  project: Project,
  workingDirectoryInfo: WorkingDirectoryInfo,
) = addWorkingDirectoryFragment(
  project,
  workingDirectoryInfo,
  { settings.externalProjectPath ?: "" },
  { settings.externalProjectPath = it }
)

fun <S> SettingsEditorFragmentContainer<S>.addWorkingDirectoryFragment(
  project: Project,
  workingDirectoryInfo: WorkingDirectoryInfo,
  getWorkingDirectory: S.() -> String,
  setWorkingDirectory: S.(String) -> Unit,
) = addLabeledSettingsEditorFragment(
  workingDirectoryInfo,
  { WorkingDirectoryField(project, workingDirectoryInfo, it) },
  { it, c -> it.getWorkingDirectory().let { p -> if (p.isNotBlank()) c.workingDirectory = p } },
  { it, c -> it.setWorkingDirectory(c.workingDirectory) }
).addValidation {
  if (it.getWorkingDirectory().isBlank()) {
    throw RuntimeConfigurationError(workingDirectoryInfo.emptyFieldError)
  }
}

fun <S> SettingsEditorFragmentContainer<S>.addDistributionFragment(
  project: Project,
  distributionsInfo: DistributionsInfo,
  getDistribution: S.() -> DistributionInfo?,
  setDistribution: S.(DistributionInfo?) -> Unit,
) = addLabeledSettingsEditorFragment(
  distributionsInfo,
  {
    DistributionComboBox(project, distributionsInfo).apply {
      specifyLocationActionName = distributionsInfo.comboBoxActionName
      if (distributionsInfo is AsyncDistributionsInfo && !distributionsInfo.isReady()) {
        this.addLoadingItem()
        application.executeOnPooledThread {
          distributionsInfo.prepare();
          application.invokeLater({
                                    this.removeLoadingItem()
                                    distributionsInfo.distributions.forEach(::addDistributionIfNotExists)
                                  }, ModalityState.stateForComponent(this))
        }
      }
      else {
        distributionsInfo.distributions.forEach(::addDistributionIfNotExists)
      }
    }
  },
  { it, c ->
    if (!c.hasLoadingItem()) {
      c.selectedDistribution = it.getDistribution()
    }
  },
  { it, c ->
    if (!c.hasLoadingItem()) {
      it.setDistribution(c.selectedDistribution)
    }
  },
)

fun <S : ExternalSystemRunConfiguration> SettingsEditorFragmentContainer<S>.addVmOptionsFragment() =
  addVmOptionsFragment(
    object : LabeledSettingsFragmentInfo {
      override val editorLabel: String = ExecutionBundle.message("run.configuration.java.vm.parameters.label")
      override val settingsId: String = "external.system.vm.options.fragment"
      override val settingsName: String = ExecutionBundle.message("run.configuration.java.vm.parameters.name")
      override val settingsGroup: String = ExecutionBundle.message("group.java.options")
      override val settingsHint: String = ExecutionBundle.message("run.configuration.java.vm.parameters.hint")
      override val settingsActionHint: String = ExecutionBundle.message("specify.vm.options.for.running.the.application")
    },
    { settings.vmOptions },
    { settings.vmOptions = it }
  )

fun <S> SettingsEditorFragmentContainer<S>.addVmOptionsFragment(
  settingsFragmentInfo: LabeledSettingsFragmentInfo,
  getVmOptions: S.() -> String?,
  setVmOptions: S.(String?) -> Unit,
) = addRemovableLabeledTextSettingsEditorFragment(
  settingsFragmentInfo,
  {
    RawCommandLineEditor().apply {
      MacrosDialog.addMacroSupport(editorField, MacrosDialog.Filters.ALL) { false }
    }
  },
  getVmOptions,
  setVmOptions
)


fun <C : ExternalSystemRunConfiguration> SettingsEditorFragmentContainer<C>.addEnvironmentFragment() =
  addEnvironmentFragment(
    object : LabeledSettingsFragmentInfo {
      override val editorLabel: String = ExecutionBundle.message("environment.variables.component.title")
      override val settingsId: String = "external.system.environment.variables.fragment"
      override val settingsName: String = ExecutionBundle.message("environment.variables.fragment.name")
      override val settingsGroup: String = ExecutionBundle.message("group.operating.system")
      override val settingsHint: String = ExecutionBundle.message("environment.variables.fragment.hint")
      override val settingsActionHint: String = ExecutionBundle.message("set.custom.environment.variables.for.the.process")
    },
    { settings.env },
    { settings.env = it },
    { settings.isPassParentEnvs },
    { settings.isPassParentEnvs = it },
    hideWhenEmpty = false
  )

fun <S> SettingsEditorFragmentContainer<S>.addEnvironmentFragment(
  settingsFragmentInfo: LabeledSettingsFragmentInfo,
  getEnvs: S.() -> Map<String, String>,
  setEnvs: S.(Map<String, String>) -> Unit,
  isPassParentEnvs: S.() -> Boolean,
  setPassParentEnvs: S.(Boolean) -> Unit,
  hideWhenEmpty: Boolean,
) = addLabeledSettingsEditorFragment(
  settingsFragmentInfo,
  { EnvironmentVariablesTextFieldWithBrowseButton() },
  { it, c ->
    c.envs = it.getEnvs()
    c.isPassParentEnvs = it.isPassParentEnvs()
  },
  { it, c ->
    it.setEnvs(c.envs)
    it.setPassParentEnvs(c.isPassParentEnvs)
  },
  { !hideWhenEmpty || it.getEnvs().isNotEmpty() || !it.isPassParentEnvs() }
).apply { isRemovable = hideWhenEmpty }

fun <S> SettingsEditorFragmentContainer<S>.addPathFragment(
  project: Project,
  pathFragmentInfo: PathFragmentInfo,
  getPath: S.() -> String,
  setPath: S.(String) -> Unit,
  defaultPath: S.() -> String = { "" },
) = addRemovableLabeledTextSettingsEditorFragment(
  pathFragmentInfo,
  {
    textFieldWithBrowseButton(
      project,
      ExtendableTextField(10).apply {
        val fileChooserMacroFilter = pathFragmentInfo.fileChooserMacroFilter
        if (fileChooserMacroFilter != null) {
          MacrosDialog.addMacroSupport(this, fileChooserMacroFilter) { false }
        }
      },
      pathFragmentInfo.fileChooserDescriptor
        .withPathToTextConvertor(::getPresentablePath)
        .withTextToPathConvertor(::getCanonicalPath)
    )
  },
  { getPath().let(::getPresentablePath).nullize() },
  { setPath(it?.let(::getCanonicalPath) ?: "") },
  { defaultPath().let(::getPresentablePath).nullize() }
)
