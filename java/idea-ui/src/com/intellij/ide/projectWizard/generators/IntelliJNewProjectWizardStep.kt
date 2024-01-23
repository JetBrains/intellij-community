// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleOnboardingTipsChangedEvent
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Intellij.logContentRootChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Intellij.logModuleFileLocationChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Intellij.logModuleNameChanged
import com.intellij.ide.projectWizard.generators.AssetsJavaNewProjectWizardStep.Companion.proposeToGenerateOnboardingTipsByDefault
import com.intellij.ide.projectWizard.projectWizardJdkComboBox
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ProjectWizardUtil
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.GENERATE_ONBOARDING_TIPS_NAME
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.MODIFIABLE_MODULE_MODEL_KEY
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import java.nio.file.Paths

abstract class IntelliJNewProjectWizardStep<ParentStep>(val parent: ParentStep) :
  AbstractNewProjectWizardStep(parent), IntelliJNewProjectWizardData
  where ParentStep : NewProjectWizardStep,
        ParentStep : NewProjectWizardBaseData {

  final override val sdkProperty = propertyGraph.property<Sdk?>(null)
  final override val sdkDownloadTaskProperty = propertyGraph.property<SdkDownloadTask?>(null)
  final override val moduleNameProperty = propertyGraph.lazyProperty(::suggestModuleName)
  final override val contentRootProperty = propertyGraph.lazyProperty(::suggestContentRoot)
  final override val moduleFileLocationProperty = propertyGraph.lazyProperty(::suggestModuleFilePath)
  final override val addSampleCodeProperty = propertyGraph.property(true)
    .bindBooleanStorage(ADD_SAMPLE_CODE_PROPERTY_NAME)
  final override val generateOnboardingTipsProperty = propertyGraph.property(proposeToGenerateOnboardingTipsByDefault())
    .bindBooleanStorage(GENERATE_ONBOARDING_TIPS_NAME)

  final override var sdk by sdkProperty
  final override var sdkDownloadTask by sdkDownloadTaskProperty
  final override var moduleName by moduleNameProperty
  final override var contentRoot by contentRootProperty
  final override var moduleFileLocation by moduleFileLocationProperty
  final override var addSampleCode by addSampleCodeProperty
  final override var generateOnboardingTips by generateOnboardingTipsProperty

  private var userDefinedContentRoot: Boolean = false
  private var userDefinedModuleFileLocation: Boolean = false

  private fun suggestModuleName(): String {
    return parent.name
  }

  private fun suggestContentRoot(): String {
    return "${parent.path}/${parent.name}"
  }

  private fun suggestModuleFilePath(): String {
    return contentRoot
  }

  init {
    moduleNameProperty.dependsOn(parent.nameProperty, ::suggestModuleName)

    contentRootProperty.dependsOn(parent.nameProperty, ::suggestContentRoot)
    contentRootProperty.dependsOn(parent.pathProperty, ::suggestContentRoot)

    moduleFileLocationProperty.dependsOn(contentRootProperty, ::suggestModuleFilePath)

    sdkProperty.afterChange {
      if (it != null) {
        service<SdkPreIndexingService>().requestPreIndexation(it)
      }
    }
  }

  protected fun setupJavaSdkUI(builder: Panel) {
    builder.row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
      projectWizardJdkComboBox(context, sdkProperty, sdkDownloadTaskProperty, StdModuleTypes.JAVA.id, context.projectJdk)
    }.bottomGap(BottomGap.SMALL)
  }

  protected fun setupSampleCodeUI(builder: Panel) {
    builder.row {
      checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
        .bindSelected(addSampleCodeProperty)
        .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
    }
  }

  protected fun setupSampleCodeWithOnBoardingTipsUI(builder: Panel) {
    builder.indent {
      row {
        checkBox(UIBundle.message("label.project.wizard.new.project.generate.onboarding.tips"))
          .bindSelected(generateOnboardingTipsProperty)
          .whenStateChangedFromUi { logAddSampleOnboardingTipsChangedEvent(it) }
      }
    }.enabledIf(addSampleCodeProperty)
  }

  protected fun setupModuleNameUI(builder: Panel) {
    builder.row(UIBundle.message("label.project.wizard.new.project.module.name")) {
      textField()
        .bindText(moduleNameProperty)
        .align(AlignX.FILL)
        .validationOnInput { validateModuleName() }
        .validationOnApply { validateModuleName() }
        .whenTextChangedFromUi { logModuleNameChanged() }
    }
  }

  protected fun setupModuleContentRootUI(builder: Panel) {
    builder.row(UIBundle.message("label.project.wizard.new.project.content.root")) {
      val browseDialogTitle = UIBundle.message("label.project.wizard.new.project.content.root.title")
      val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        .withPathToTextConvertor(::getPresentablePath)
        .withTextToPathConvertor(::getCanonicalPath)
      textFieldWithBrowseButton(browseDialogTitle, context.project, fileChooserDescriptor)
        .bindText(contentRootProperty.toUiPathProperty())
        .align(AlignX.FILL)
        .validationOnApply { validateContentRoot() }
        .whenTextChangedFromUi { userDefinedContentRoot = true }
        .whenTextChangedFromUi { logContentRootChanged() }
    }
  }

  protected fun setupModuleFileLocationUI(builder: Panel) {
    builder.row(UIBundle.message("label.project.wizard.new.project.module.file.location")) {
      val browseDialogTitle = UIBundle.message("label.project.wizard.new.project.module.file.location.title")
      val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        .withPathToTextConvertor(::getPresentablePath)
        .withTextToPathConvertor(::getCanonicalPath)
      textFieldWithBrowseButton(browseDialogTitle, context.project, fileChooserDescriptor)
        .bindText(moduleFileLocationProperty.toUiPathProperty())
        .align(AlignX.FILL)
        .validationOnApply { validateModuleFileLocation() }
        .whenTextChangedFromUi { userDefinedModuleFileLocation = true }
        .whenTextChangedFromUi { logModuleFileLocationChanged() }
    }
  }

  protected open fun setupSettingsUI(builder: Panel) {
    setupJavaSdkUI(builder)
    @Suppress("DEPRECATION")
    builder.customOptions()
    setupSampleCodeUI(builder)
  }

  protected open fun setupAdvancedSettingsUI(builder: Panel) {
    setupModuleNameUI(builder)
    setupModuleContentRootUI(builder)
    setupModuleFileLocationUI(builder)
    @Suppress("DEPRECATION")
    builder.customAdditionalOptions()
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Implements setupSettingsUI function directly")
  open fun Panel.customOptions() = Unit

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Implements setupAdvancedSettingsUI function directly")
  open fun Panel.customAdditionalOptions() = Unit

  override fun setupUI(builder: Panel) {
    setupSettingsUI(builder)
    builder.collapsibleGroup(UIBundle.message("label.project.wizard.new.project.advanced.settings")) {
      setupAdvancedSettingsUI(this)
    }.topGap(TopGap.MEDIUM)
  }

  private fun ValidationInfoBuilder.validateModuleName(): ValidationInfo? {
    if (moduleName.isEmpty()) return error(JavaUiBundle.message("module.name.location.dialog.message.enter.module.name"))

    // Name uniqueness
    val project = context.project
    if (project != null) {
      val module = when (val model = ProjectStructureConfigurable.getInstance(project)?.context?.modulesConfigurator?.moduleModel) {
        null -> ModuleManager.getInstance(project)?.findModuleByName(moduleName)
        else -> model.findModuleByName(moduleName)
      }
      if (module != null) return error(
        JavaUiBundle.message("module.name.location.dialog.message.module.already.exist.in.project", moduleName))
    }
    return null
  }

  private fun ValidationInfoBuilder.validateContentRoot(): ValidationInfo? {
    if (!ProjectWizardUtil.createDirectoryIfNotExists(JavaUiBundle.message("directory.module.content.root"), contentRoot,
        userDefinedContentRoot))
      return error(JavaUiBundle.message("module.name.location.dialog.message.error.content.root", contentRoot))
    return null
  }

  private fun ValidationInfoBuilder.validateModuleFileLocation(): ValidationInfo? {
    if (moduleFileLocation.isEmpty())
      return error(JavaUiBundle.message("module.name.location.dialog.message.enter.module.file.location"))
    if (!ProjectWizardUtil.createDirectoryIfNotExists(JavaUiBundle.message("directory.module.file"), moduleFileLocation,
        userDefinedModuleFileLocation))
      return error(JavaUiBundle.message("module.name.location.dialog.message.error.module.file.location", moduleFileLocation))
    return null
  }

  private fun configureModuleBuilder(project: Project, builder: ModuleBuilder) {
    val moduleFile = Paths.get(moduleFileLocation, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION)

    builder.name = moduleName
    builder.moduleFilePath = FileUtil.toSystemDependentName(moduleFile.toString())
    builder.contentEntryPath = FileUtil.toSystemDependentName(contentRoot)

    if (context.isCreatingNewProject) {
      // New project with a single module: set project JDK
      context.projectJdk = sdk
    }
    else {
      // New module in an existing project: set module JDK
      val isSameSdk = ProjectRootManager.getInstance(project).projectSdk?.name == sdk?.name
      builder.moduleJdk = if (isSameSdk) null else sdk
    }
  }

  private fun startJdkDownloadIfNeeded(module: Module) {
    val sdkDownloadTask = sdkDownloadTask
    if (sdkDownloadTask is JdkDownloadTask) {
      // Download the SDK on project creation
      module.project.service<JdkDownloadService>().downloadJdk(sdkDownloadTask, module, context.isCreatingNewProject)
    }
  }

  fun setupProject(project: Project, builder: ModuleBuilder) {
    configureModuleBuilder(project, builder)

    val model = context.getUserData(MODIFIABLE_MODULE_MODEL_KEY)
    val module = builder.commit(project, model)?.singleOrNull()

    module?.let(::startJdkDownloadIfNeeded)
  }
}