// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Intellij.logContentRootChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Intellij.logModuleFileLocationChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Intellij.logModuleNameChanged
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.ide.projectWizard.projectWizardJdkComboBox
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ProjectWizardUtil
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.ide.wizard.setupProjectFromBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
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

  final override val jdkIntentProperty = propertyGraph.property<ProjectWizardJdkIntent?>(null)
  val sdkDownloadTaskProperty = jdkIntentProperty.transform { intent -> intent?.downloadTask }
  final override val moduleNameProperty = propertyGraph.lazyProperty(::suggestModuleName)
  final override val contentRootProperty = propertyGraph.lazyProperty(::suggestContentRoot)
  final override val moduleFileLocationProperty = propertyGraph.lazyProperty(::suggestModuleFilePath)
  final override val addSampleCodeProperty = propertyGraph.property(true)
    .bindBooleanStorage(ADD_SAMPLE_CODE_PROPERTY_NAME)

  final override var jdkIntent by jdkIntentProperty
  val sdkDownloadTask by sdkDownloadTaskProperty
  final override var moduleName by moduleNameProperty
  final override var contentRoot by contentRootProperty
  final override var moduleFileLocation by moduleFileLocationProperty
  final override var addSampleCode by addSampleCodeProperty

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

    jdkIntentProperty.afterChange {
      if (it is ProjectWizardJdkIntent.ExistingJdk) {
        service<SdkPreIndexingService>().requestPreIndexation(it.jdk)
      }
    }
  }

  protected fun setupJavaSdkUI(builder: Panel) {
    builder.row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
      projectWizardJdkComboBox(this, jdkIntentProperty)
        .whenItemSelectedFromUi { jdkIntent?.javaVersion?.let { logSdkChanged(it.feature) } }
        .onApply { jdkIntent?.javaVersion?.let { logSdkFinished(it.feature) } }
    }.bottomGap(BottomGap.SMALL)
  }

  protected fun setupSampleCodeUI(builder: Panel) {
    builder.row {
      checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
        .bindSelected(addSampleCodeProperty)
        .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
        .onApply { logAddSampleCodeFinished(addSampleCode) }
    }
  }

  @Deprecated("The onboarding tips generated unconditionally")
  protected fun setupSampleCodeWithOnBoardingTipsUI(builder: Panel) = Unit

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
      val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        .withTitle(UIBundle.message("label.project.wizard.new.project.content.root.title"))
        .withPathToTextConvertor(::getPresentablePath)
        .withTextToPathConvertor(::getCanonicalPath)
      textFieldWithBrowseButton(fileChooserDescriptor, context.project)
        .bindText(contentRootProperty.toUiPathProperty())
        .align(AlignX.FILL)
        .validationOnApply { validateContentRoot() }
        .whenTextChangedFromUi { userDefinedContentRoot = true }
        .whenTextChangedFromUi { logContentRootChanged() }
    }
  }

  protected fun setupModuleFileLocationUI(builder: Panel) {
    builder.row(UIBundle.message("label.project.wizard.new.project.module.file.location")) {
      val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        .withTitle(UIBundle.message("label.project.wizard.new.project.module.file.location.title"))
        .withPathToTextConvertor(::getPresentablePath)
        .withTextToPathConvertor(::getCanonicalPath)
      textFieldWithBrowseButton(fileChooserDescriptor, context.project)
        .bindText(moduleFileLocationProperty.toUiPathProperty())
        .align(AlignX.FILL)
        .validationOnApply { validateModuleFileLocation() }
        .whenTextChangedFromUi { userDefinedModuleFileLocation = true }
        .whenTextChangedFromUi { logModuleFileLocationChanged() }
    }
  }

  protected open fun setupSettingsUI(builder: Panel) {
    setupJavaSdkUI(builder)
    setupSampleCodeUI(builder)
  }

  protected open fun setupAdvancedSettingsUI(builder: Panel) {
    setupModuleNameUI(builder)
    setupModuleContentRootUI(builder)
    setupModuleFileLocationUI(builder)
  }

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

    configureSdk(project, builder)
  }

  private fun configureSdk(project: Project, builder: ModuleBuilder) {
    if (!context.isCreatingNewProject) {
      // New module in an existing project: set module JDK
      val isSameSdk = ProjectRootManager.getInstance(project).projectSdk?.name == jdkIntent?.name
      builder.moduleJdk = if (isSameSdk) null else context.projectJdk
    }
  }

  private fun startJdkDownloadIfNeeded(module: Module) {
    val sdkDownloadTask = sdkDownloadTask
    if (sdkDownloadTask is JdkDownloadTask) {
      // Download the SDK on project creation
      module.project.service<JdkDownloadService>().scheduleDownloadJdk(sdkDownloadTask, module, context.isCreatingNewProject)
    }
  }

  fun setupProject(project: Project, builder: ModuleBuilder) {
    configureModuleBuilder(project, builder)
    setupProjectFromBuilder(project, builder)
      ?.let(::startJdkDownloadIfNeeded)
  }
}
