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
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryInfo
import com.intellij.openapi.externalSystem.service.ui.util.DistributionsInfo
import com.intellij.openapi.externalSystem.service.ui.util.LabeledSettingsFragmentInfo
import com.intellij.openapi.externalSystem.service.ui.util.PathFragmentInfo
import com.intellij.openapi.externalSystem.service.ui.util.SettingsFragmentInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.distribution.DistributionComboBox
import com.intellij.openapi.roots.ui.distribution.DistributionInfo
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.TextAccessor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.*
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.text.JTextComponent


fun <S : RunConfigurationBase<*>> SettingsFragmentsContainer<S>.addBeforeRunFragment(buildTaskKey: Key<*>) =
  add(createBeforeRunFragment(buildTaskKey))

fun <S : RunConfigurationBase<*>> createBeforeRunFragment(buildTaskKey: Key<*>): BeforeRunFragment<S> {
  val parentDisposable = Disposer.newDisposable()
  val beforeRunComponent = BeforeRunComponent(parentDisposable)
  val beforeRunFragment = BeforeRunFragment.createBeforeRun<S>(beforeRunComponent, buildTaskKey)
  Disposer.register(beforeRunFragment, parentDisposable)
  return beforeRunFragment
}

inline fun <S, reified V : Enum<V>> SettingsFragmentsContainer<S>.addVariantFragment(
  info: LabeledSettingsFragmentInfo,
  crossinline getter: S.() -> V,
  crossinline setter: S.(V) -> Unit,
  crossinline getText: (V) -> String
) = addLabeledSettingsEditorFragment(
  ComboBox(CollectionComboBoxModel(EnumSet.allOf(V::class.java).toList())),
  info,
  { it, c -> c.selectedItem = it.getter() },
  { it, c -> it.setter(c.selectedItem!! as V) },
).applyToComponent {
  this.component.setRenderer(object : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
      list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ) = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      .also { setText(getText(value!! as V)) }
  })
}

@Suppress("HardCodedStringLiteral")
inline fun <S, reified V : Enum<V>> SettingsFragmentsContainer<S>.addVariantTag(
  id: String,
  @Nls(capitalization = Nls.Capitalization.Sentence) name: String,
  @Nls(capitalization = Nls.Capitalization.Title) group: String?,
  crossinline getter: S.() -> V,
  crossinline setter: S.(V) -> Unit,
  crossinline getText: (V) -> String
) = add(VariantTagFragment.createFragment(
  id,
  name,
  group,
  { EnumSet.allOf(V::class.java).toTypedArray() },
  { it.getter() },
  { it, v -> it.setter(v) },
  { it.getter() != EnumSet.allOf(V::class.java).first() }
)!!).apply {
  setVariantNameProvider { getText(it) }
}

fun <S> SettingsFragmentsContainer<S>.addTag(
  id: String,
  @Nls name: String,
  @Nls group: String,
  @Nls hint: String?,
  getter: S.() -> Boolean,
  setter: S.(Boolean) -> Unit
) = add(SettingsEditorFragment.createTag(
  id,
  name,
  group,
  { it.getter() },
  { it, v -> it.setter(v) }
)!!).apply {
  actionHint = hint
}

fun <S> SettingsFragmentsContainer<S>.addCommandLineFragment(
  project: Project,
  commandLineInfo: CommandLineInfo,
  getCommandLine: S.() -> String,
  setCommandLine: S.(String) -> Unit
) = addSettingsEditorFragment(
  CommandLineField(project, commandLineInfo),
  commandLineInfo,
  { it, c -> c.commandLine = it.getCommandLine() },
  { it, c -> it.setCommandLine(c.commandLine) },
)

fun <S : ExternalSystemRunConfiguration> SettingsFragmentsContainer<S>.addWorkingDirectoryFragment(
  project: Project,
  workingDirectoryInfo: WorkingDirectoryInfo
) = addWorkingDirectoryFragment(
  project,
  workingDirectoryInfo,
  { settings.externalProjectPath ?: "" },
  { settings.externalProjectPath = it }
)

fun <S> SettingsFragmentsContainer<S>.addWorkingDirectoryFragment(
  project: Project,
  workingDirectoryInfo: WorkingDirectoryInfo,
  getWorkingDirectory: S.() -> String,
  setWorkingDirectory: S.(String) -> Unit
) = addLabeledSettingsEditorFragment(
  WorkingDirectoryField(project, workingDirectoryInfo),
  workingDirectoryInfo,
  { it, c -> it.getWorkingDirectory().let { p -> if (p.isNotBlank()) c.workingDirectory = p } },
  { it, c -> it.setWorkingDirectory(c.workingDirectory) }
).addValidation {
  if (it.getWorkingDirectory().isBlank()) {
    throw RuntimeConfigurationError(workingDirectoryInfo.emptyFieldError)
  }
}

fun <S> SettingsFragmentsContainer<S>.addDistributionFragment(
  project: Project,
  distributionsInfo: DistributionsInfo,
  getDistribution: S.() -> DistributionInfo?,
  setDistribution: S.(DistributionInfo?) -> Unit
) = addLabeledSettingsEditorFragment(
  DistributionComboBox(project, distributionsInfo).apply {
    specifyLocationActionName = distributionsInfo.comboBoxActionName
    distributionsInfo.distributions.forEach(::addDistributionIfNotExists)
  },
  distributionsInfo,
  { it, c -> c.selectedDistribution = it.getDistribution() },
  { it, c -> it.setDistribution(c.selectedDistribution) },
)

fun <S : ExternalSystemRunConfiguration> SettingsFragmentsContainer<S>.addVmOptionsFragment() =
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

fun <S> SettingsFragmentsContainer<S>.addVmOptionsFragment(
  info: LabeledSettingsFragmentInfo,
  getVmOptions: S.() -> String?,
  setVmOptions: S.(String?) -> Unit
) = addRemovableLabeledTextSettingsEditorFragment(
  RawCommandLineEditor().apply {
    MacrosDialog.addMacroSupport(editorField, MacrosDialog.Filters.ALL) { false }
  },
  info,
  getVmOptions,
  setVmOptions
)


fun <C : ExternalSystemRunConfiguration> SettingsFragmentsContainer<C>.addEnvironmentFragment() =
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

fun <S> SettingsFragmentsContainer<S>.addEnvironmentFragment(
  info: LabeledSettingsFragmentInfo,
  getEnvs: S.() -> Map<String, String>,
  setEnvs: S.(Map<String, String>) -> Unit,
  isPassParentEnvs: S.() -> Boolean,
  setPassParentEnvs: S.(Boolean) -> Unit,
  hideWhenEmpty: Boolean
) = addLabeledSettingsEditorFragment(
  EnvironmentVariablesTextFieldWithBrowseButton(),
  info,
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

fun <S> SettingsFragmentsContainer<S>.addPathFragment(
  project: Project,
  pathFragmentInfo: PathFragmentInfo,
  getPath: S.() -> String,
  setPath: S.(String) -> Unit,
  defaultPath: S.() -> String = { "" }
) = addRemovableLabeledTextSettingsEditorFragment(
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
  ) { getPresentablePath(it.path) },
  pathFragmentInfo,
  { getPath().let(::getPresentablePath).nullize() },
  { setPath(it?.let(::getCanonicalPath) ?: "") },
  { defaultPath().let(::getPresentablePath).nullize() }
)

fun <S, C> SettingsFragmentsContainer<S>.addRemovableLabeledTextSettingsEditorFragment(
  component: C,
  info: LabeledSettingsFragmentInfo,
  getter: S.() -> String?,
  setter: S.(String?) -> Unit,
  default: S.() -> String? = { null }
) where C : JComponent, C : TextAccessor = addRemovableLabeledSettingsEditorFragment(
  component,
  info,
  TextAccessor::getText,
  TextAccessor::setText,
  getter,
  setter,
  default
)

fun <S, C : JComponent, V> SettingsFragmentsContainer<S>.addRemovableLabeledSettingsEditorFragment(
  component: C,
  info: LabeledSettingsFragmentInfo,
  getterC: C.() -> V,
  setterC: C.(V) -> Unit,
  getterS: S.() -> V?,
  setterS: S.(V?) -> Unit,
  defaultS: S.() -> V? = { null }
): SettingsEditorFragment<S, SettingsEditorLabeledComponent<C>> {
  val ref = Ref<SettingsEditorFragment<S, SettingsEditorLabeledComponent<C>>>()
  return addLabeledSettingsEditorFragment(
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

fun <S, C : JComponent> SettingsFragmentsContainer<S>.addLabeledSettingsEditorFragment(
  component: C,
  settingsFragmentInfo: LabeledSettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
) = addLabeledSettingsEditorFragment(component, settingsFragmentInfo, reset, apply) { true }
  .apply { isRemovable = false }

fun <S, C : JComponent> SettingsFragmentsContainer<S>.addSettingsEditorFragment(
  component: C,
  settingsFragmentInfo: SettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
) = addSettingsEditorFragment(component, settingsFragmentInfo, reset, apply) { true }
  .apply { isRemovable = false }

fun <S, C : JComponent> SettingsFragmentsContainer<S>.addLabeledSettingsEditorFragment(
  component: C,
  info: LabeledSettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
  initialSelection: (S) -> Boolean
) = addSettingsEditorFragment(
  SettingsEditorLabeledComponent(info.editorLabel, component),
  info,
  { it, c -> reset(it, c.component) },
  { it, c -> apply(it, c.component) },
  initialSelection
)

fun <S, C : JComponent> SettingsFragmentsContainer<S>.addSettingsEditorFragment(
  component: C,
  settingsFragmentInfo: SettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
  initialSelection: (S) -> Boolean,
) = add(SettingsEditorFragment(
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
  if (settingsFragmentInfo.settingsType == SettingsEditorFragmentType.COMMAND_LINE) {
    if (editorComponent is JTextComponent ||
        editorComponent is JComboBox<*>) {
      CommonParameterFragments.setMonospaced(editorComponent)
    }
  }
  if (editorComponent is JBTextField) {
    FragmentedSettingsUtil.setupPlaceholderVisibility(editorComponent)
  }
})

fun <S, C : JComponent, F : SettingsEditorFragment<S, C>> F.applyToComponent(action: C.() -> Unit): F = apply {
  component().action()
}

class SettingsEditorLabeledComponent<C : JComponent>(label: @NlsContexts.Label String, component: C) : LabeledComponent<C>() {
  fun modifyComponentSize(configure: C.() -> Unit) {
    layout = WrapLayout(FlowLayout.LEADING, UIUtil.DEFAULT_HGAP, 2)
    border = JBUI.Borders.empty(0, -UIUtil.DEFAULT_HGAP, 0, 0)
    component.configure()
  }

  init {
    text = label
    labelLocation = BorderLayout.WEST
    setComponent(component)
  }
}

fun <C : JComponent, F : SettingsEditorFragment<*, SettingsEditorLabeledComponent<C>>> F.modifyLabeledComponentSize(configure: C.() -> Unit) =
  apply { component().modifyComponentSize(configure) }