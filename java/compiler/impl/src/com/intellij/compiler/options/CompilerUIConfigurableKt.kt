// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.options

import com.intellij.codeInsight.NullableNotNullDialog
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.compiler.ParallelCompilationOption
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.compiler.options.CompilerOptionsFilter.Setting
import com.intellij.compiler.options.CompilerUIConfigurable.applyResourcePatterns
import com.intellij.compiler.server.BuildManager
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls
import java.util.*
import javax.swing.*

class CompilerUIConfigurableKt(val project: Project) : DslConfigurableBase(), SearchableConfigurable {
  private val disabledSettings: MutableSet<Setting> = EnumSet.noneOf(Setting::class.java)

  private lateinit var resourcePatternsField: RawCommandLineEditor

  private lateinit var cbClearOutputDirectory: JCheckBox
  private lateinit var cbAssertNotNull: JCheckBox

  private lateinit var cbAutoShowFirstError: JCheckBox
  private lateinit var cbDisplayNotificationPopup: JCheckBox
  private lateinit var cbEnableAutomakeCell: Cell<JCheckBox>
  private lateinit var cbEnableAutomake: JCheckBox

  private lateinit var comboboxJpsParallelCompilation: JComboBox<ParallelCompilationOption>

  lateinit var sharedHeapSizeField: JTextField
  lateinit var sharedVMOptionsField: ExpandableTextField
  lateinit var heapSizeField: JTextField
  lateinit var vmOptionsField: ExpandableTextField

  private lateinit var cbRebuildOnDependencyChange: JCheckBox

  private lateinit var configureAnnotations: JButton

  override fun createPanel() = panel {
    row(JavaCompilerBundle.message("label.option.resource.patterns.text")) {
      resourcePatternsField = cell(RawCommandLineEditor(ParametersListUtil.COLON_LINE_PARSER, ParametersListUtil.COLON_LINE_JOINER))
        .comment(JavaCompilerBundle.message("compiler.ui.pattern.legend.text"))
        .resizableColumn()
        .align(AlignX.FILL)
        .component
      contextHelp(JavaCompilerBundle.message("compiler.ui.pattern.context.help"), JavaCompilerBundle.message("compiler.ui.pattern.context.help.title"))
    }
      .bottomGap(BottomGap.SMALL)

    panel {
      row {
        cbClearOutputDirectory = checkBox(JavaCompilerBundle.message("label.option.clear.output.directory.on.rebuild"))
          .comment(JavaCompilerBundle.message("settings.warning"))
          .component
      }
      row {
        cbAssertNotNull = checkBox(JavaCompilerBundle.message("add.notnull.assertions"))
          .component
        configureAnnotations = link(JavaCompilerBundle.message("settings.configure.annotations")) {
          NullableNotNullDialog.showDialogWithInstrumentationOptions(project)
          cbAssertNotNull.setSelected(!NullableNotNullManager.getInstance(project).getInstrumentedNotNulls().isEmpty())
        }
          .component
      }
      row {
        cbAutoShowFirstError = checkBox(JavaCompilerBundle.message("label.option.autoshow.first.error"))
          .component
      }
      row {
        cbDisplayNotificationPopup = checkBox(JavaCompilerBundle.message("label.option.display.notification.popup"))
          .component
      }
      row {
        cbEnableAutomakeCell = checkBox(JavaCompilerBundle.message("settings.build.project.automatically"))
          .comment(JavaCompilerBundle.message("only.works.while.not.running.debugging"))
        cbEnableAutomake = cbEnableAutomakeCell.component
      }
      row {
        cbRebuildOnDependencyChange = checkBox(JavaCompilerBundle.message("settings.rebuild.module.on.dependency.change"))
          .component
      }
      row {
        label(JavaCompilerBundle.message("settings.compile.independent.modules.in.parallel"))
        comboboxJpsParallelCompilation = comboBox(ParallelCompilationOption.entries)
          .component
        contextHelp(
          title = JavaCompilerBundle.message("settings.parallel.module.compile.context.help.title"),
          description = JavaCompilerBundle.message("settings.parallel.module.compile.context.help.description")
        )
      }
    }

    group(JavaCompilerBundle.message("settings.build.process.group")) {
      row(JavaCompilerBundle.message("settings.build.process.heap.size")) {
        sharedHeapSizeField = intTextField()
          .gap(RightGap.SMALL)
          .component
        label(JavaCompilerBundle.message("settings.size.mbytes"))
      }
      row(JavaCompilerBundle.message("settings.shared.build.process.vm.options")) {
        sharedVMOptionsField = expandableTextField()
          .resizableColumn()
          .align(AlignX.FILL)
          .component
      }
      row(JavaCompilerBundle.message("settings.user.local.build.process.heap.size")) {
        heapSizeField = intTextField()
          .gap(RightGap.SMALL)
          .comment(JavaCompilerBundle.message("settings.user.local.build.process.heap.size.comment"))
          .component
        label(JavaCompilerBundle.message("settings.size.mbytes"))
      }
      row(JavaCompilerBundle.message("settings.user.local.build.process.vm.options")) {
        vmOptionsField = expandableTextField()
          .comment(JavaCompilerBundle.message("settings.user.local.build.process.vm.options.comment"))
          .resizableColumn()
          .align(AlignX.FILL)
          .component
      }
        .bottomGap(BottomGap.SMALL)
    }

    tweakControls()

    val updateStateListener: DocumentAdapter = object : DocumentAdapter() {
      override fun textChanged(e: javax.swing.event.DocumentEvent) {
        sharedVMOptionsField.isEditable = vmOptionsField.document.length == 0
        sharedVMOptionsField.background = when (sharedVMOptionsField.isEditable) {
          true -> UIUtil.getTextFieldBackground()
          else -> UIUtil.getTextFieldDisabledBackground()
        }
        sharedHeapSizeField.isEnabled = heapSizeField.document.length == 0 &&
                                        ParametersListUtil.parse(vmOptionsField.text).none { StringUtil.startsWithIgnoreCase(it, "-Xmx") }
      }
    }

    vmOptionsField.document.addDocumentListener(updateStateListener)
    heapSizeField.document.addDocumentListener(updateStateListener)
  }

  private fun tweakControls() {
    val managers = CompilerOptionsFilter.EP_NAME.extensionList
    var showExternalBuildSetting = true
    for (manager in managers) {
      showExternalBuildSetting = manager.isAvailable(Setting.EXTERNAL_BUILD, project)
      if (!showExternalBuildSetting) {
        disabledSettings.add(Setting.EXTERNAL_BUILD)
        break
      }
    }

    for (setting in Setting.entries) {
      if (!showExternalBuildSetting && CompilerUIConfigurable.EXTERNAL_BUILD_SETTINGS.contains(setting)) {
        // Disable all nested external compiler settings if 'use external build' is unavailable.
        disabledSettings.add(setting)
      }
      else {
        for (manager in managers) {
          if (!manager.isAvailable(setting, project)) {
            disabledSettings.add(setting)
            break
          }
        }
      }
    }

    val controls: Map<Setting, Collection<JComponent>> = mapOf(
      Setting.RESOURCE_PATTERNS to listOf(resourcePatternsField),
      Setting.CLEAR_OUTPUT_DIR_ON_REBUILD to listOf(cbClearOutputDirectory),
      Setting.ADD_NOT_NULL_ASSERTIONS to listOf(cbAssertNotNull),
      Setting.AUTO_SHOW_FIRST_ERROR_IN_EDITOR to listOf(cbAutoShowFirstError),
      Setting.DISPLAY_NOTIFICATION_POPUP to listOf(cbDisplayNotificationPopup),
      Setting.AUTO_MAKE to listOf(cbEnableAutomake),
      Setting.PARALLEL_COMPILATION to listOf(comboboxJpsParallelCompilation),
      Setting.REBUILD_MODULE_ON_DEPENDENCY_CHANGE to listOf(cbRebuildOnDependencyChange),
      Setting.HEAP_SIZE to listOf(heapSizeField, sharedHeapSizeField),
      Setting.COMPILER_VM_OPTIONS to listOf(vmOptionsField, sharedVMOptionsField),
    )

    for (setting in disabledSettings) {
      val components = controls[setting]
      if (components != null) {
        components.forEach { it.isVisible = false }
      }
    }
  }

  override fun apply() {
    val configuration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
    val workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(project)
    if (!disabledSettings.contains(Setting.AUTO_SHOW_FIRST_ERROR_IN_EDITOR)) {
      workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR = cbAutoShowFirstError.isSelected
    }
    if (!disabledSettings.contains(Setting.DISPLAY_NOTIFICATION_POPUP)) {
      workspaceConfiguration.DISPLAY_NOTIFICATION_POPUP = cbDisplayNotificationPopup.isSelected
    }
    if (!disabledSettings.contains(Setting.CLEAR_OUTPUT_DIR_ON_REBUILD)) {
      workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY = cbClearOutputDirectory.isSelected
    }
    if (!disabledSettings.contains(Setting.EXTERNAL_BUILD)) {
      if (!disabledSettings.contains(Setting.AUTO_MAKE)) {
        workspaceConfiguration.MAKE_PROJECT_ON_SAVE = cbEnableAutomake.isSelected
      }
      if (!disabledSettings.contains(Setting.PARALLEL_COMPILATION)) {
        configuration.parallelCompilationOption = comboboxJpsParallelCompilation.getItemAt(comboboxJpsParallelCompilation.selectedIndex)
      }
      if (!disabledSettings.contains(Setting.REBUILD_MODULE_ON_DEPENDENCY_CHANGE)) {
        workspaceConfiguration.REBUILD_ON_DEPENDENCY_CHANGE = cbRebuildOnDependencyChange.isSelected
      }
      if (!disabledSettings.contains(Setting.HEAP_SIZE)) {
        try {
          val heapSizeText: String = heapSizeField.getText().trim()
          workspaceConfiguration.COMPILER_PROCESS_HEAP_SIZE = if (heapSizeText.isEmpty()) 0 else heapSizeText.toInt()
          configuration.setBuildProcessHeapSize(sharedHeapSizeField.getText().trim().toInt())
        }
        catch (ignored: NumberFormatException) {}
      }
      if (!disabledSettings.contains(Setting.COMPILER_VM_OPTIONS)) {
        workspaceConfiguration.COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS = vmOptionsField.getText().trim()
        configuration.buildProcessVMOptions = sharedVMOptionsField.getText().trim()
      }
    }

    if (!disabledSettings.contains(Setting.ADD_NOT_NULL_ASSERTIONS)) {
      configuration.isAddNotNullAssertions = cbAssertNotNull.isSelected
    }
    if (!disabledSettings.contains(Setting.RESOURCE_PATTERNS)) {
      configuration.removeResourceFilePatterns()
      val extensionString: String = resourcePatternsField.text.trim()
      applyResourcePatterns(extensionString, configuration)
    }

    if (!project.isDefault) {
      BuildManager.getInstance().clearState(project)
    }
  }

  override fun reset() {
    val configuration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
    val workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(project)

    cbAutoShowFirstError.setSelected(workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR)
    cbDisplayNotificationPopup.setSelected(workspaceConfiguration.DISPLAY_NOTIFICATION_POPUP)
    cbClearOutputDirectory.setSelected(workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY)
    cbAssertNotNull.setSelected(configuration.isAddNotNullAssertions)
    cbEnableAutomake.setSelected(workspaceConfiguration.MAKE_PROJECT_ON_SAVE)
    cbRebuildOnDependencyChange.setSelected(workspaceConfiguration.REBUILD_ON_DEPENDENCY_CHANGE)
    comboboxJpsParallelCompilation.selectedItem = configuration.parallelCompilationOption
    val heapSize = workspaceConfiguration.COMPILER_PROCESS_HEAP_SIZE
    heapSizeField.text = if (heapSize > 0) heapSize.toString() else ""
    // for compatibility with older projects
    val javacPreferred = JavacConfiguration.getOptions(project, JavacConfiguration::class.java).MAXIMUM_HEAP_SIZE
    sharedHeapSizeField.text = configuration.getBuildProcessHeapSize(javacPreferred).toString()
    val options = workspaceConfiguration.COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS
    vmOptionsField.setText(options?.trim() ?: "")
    sharedVMOptionsField.setText(configuration.buildProcessVMOptions)

    configuration.convertPatterns()

    resourcePatternsField.text = CompilerUIConfigurable.patternsToString(configuration.resourceFilePatterns)

    if (PowerSaveMode.isEnabled()) {
      cbEnableAutomakeCell.comment(JavaCompilerBundle.message("disabled.in.power.save.mode"), 70, HyperlinkEventAction.HTML_HYPERLINK_INSTANCE)
    }
    else {
      cbEnableAutomakeCell.comment(JavaCompilerBundle.message("only.works.while.not.running.debugging"), 70, HyperlinkEventAction.HTML_HYPERLINK_INSTANCE)
    }
  }

  override fun isModified(): Boolean {
    val configuration = CompilerConfiguration.getInstance(project)
    val workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(project)

    var isModified = !disabledSettings.contains(Setting.AUTO_SHOW_FIRST_ERROR_IN_EDITOR)
                     && ComparingUtils.isModified(cbAutoShowFirstError, workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR)
    isModified = isModified || !disabledSettings.contains(Setting.DISPLAY_NOTIFICATION_POPUP)
                 && ComparingUtils.isModified(cbDisplayNotificationPopup, workspaceConfiguration.DISPLAY_NOTIFICATION_POPUP)
    isModified = isModified || !disabledSettings.contains(Setting.AUTO_MAKE)
                 && ComparingUtils.isModified(cbEnableAutomake, workspaceConfiguration.MAKE_PROJECT_ON_SAVE)
    isModified = isModified || !disabledSettings.contains(Setting.PARALLEL_COMPILATION)
                 && (comboboxJpsParallelCompilation.selectedItem != configuration.parallelCompilationOption)
    isModified = isModified || !disabledSettings.contains(Setting.REBUILD_MODULE_ON_DEPENDENCY_CHANGE)
                 && ComparingUtils.isModified(cbRebuildOnDependencyChange, workspaceConfiguration.REBUILD_ON_DEPENDENCY_CHANGE)
    isModified = isModified || !disabledSettings.contains(Setting.HEAP_SIZE)
                 && ComparingUtils.isModified(heapSizeField, 0, workspaceConfiguration.COMPILER_PROCESS_HEAP_SIZE)
    isModified = isModified || !disabledSettings.contains(Setting.COMPILER_VM_OPTIONS)
                 && ComparingUtils.isModified(vmOptionsField, workspaceConfiguration.COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS)

    val compilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
    isModified = isModified || !disabledSettings.contains(Setting.HEAP_SIZE)
                 && ComparingUtils.isModified(sharedHeapSizeField, compilerConfiguration.getBuildProcessHeapSize(0))
    isModified = isModified || !disabledSettings.contains(Setting.COMPILER_VM_OPTIONS)
                 && ComparingUtils.isModified(sharedVMOptionsField, compilerConfiguration.buildProcessVMOptions)
    isModified = isModified || !disabledSettings.contains(Setting.ADD_NOT_NULL_ASSERTIONS)
                 && ComparingUtils.isModified(cbAssertNotNull, compilerConfiguration.isAddNotNullAssertions)
    isModified = isModified || !disabledSettings.contains(Setting.CLEAR_OUTPUT_DIR_ON_REBUILD)
                 && ComparingUtils.isModified(cbClearOutputDirectory, workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY)
    isModified = isModified || !disabledSettings.contains(Setting.RESOURCE_PATTERNS)
                 && ComparingUtils.isModified(resourcePatternsField, CompilerUIConfigurable.patternsToString(compilerConfiguration.resourceFilePatterns))

    return isModified
  }

  fun getBuildOnSaveCheckBox(): JCheckBox {
    return cbEnableAutomake
  }

  override fun getId(): @NonNls String = "compiler.general"
  override fun getDisplayName(): @NlsContexts.ConfigurableName String = JavaCompilerBundle.message("configurable.CompilerUIConfigurable.display.name")
}
