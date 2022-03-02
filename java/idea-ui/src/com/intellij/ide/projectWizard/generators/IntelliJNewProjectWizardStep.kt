// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.util.projectWizard.ProjectWizardUtil
import com.intellij.ide.wizard.*
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

abstract class IntelliJNewProjectWizardStep<ParentStep>(val parent: ParentStep) :
  AbstractNewProjectWizardStep(parent), IntelliJNewProjectWizardData
  where ParentStep : NewProjectWizardStep,
        ParentStep : NewProjectWizardBaseData {

  val sdkProperty = propertyGraph.property<Sdk?>(null)
  val moduleNameProperty = propertyGraph.lazyProperty(::suggestModuleName)
  val contentRootProperty = propertyGraph.lazyProperty(::suggestContentRoot)
  val moduleFileLocationProperty = propertyGraph.lazyProperty(::suggestModuleFilePath)
  val addSampleCodeProperty = propertyGraph.property(false)
    .bindBooleanStorage("NewProjectWizard.addSampleCodeState")

  final override var sdk by sdkProperty
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
  }

  override fun setupUI(builder: Panel) {
    with(builder) {
      row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
        val sdkTypeFilter = { it: SdkTypeId -> it is JavaSdkType && it !is DependentSdkType }
        sdkComboBox(context, sdkProperty, StdModuleTypes.JAVA.id, sdkTypeFilter)
          .columns(COLUMNS_MEDIUM)
      }
      customOptions()
      row {
        checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
          .bindSelected(addSampleCodeProperty)
      }.topGap(TopGap.SMALL)
      collapsibleGroup(UIBundle.message("label.project.wizard.new.project.advanced.settings")) {
        row(UIBundle.message("label.project.wizard.new.project.module.name")) {
          textField()
            .bindText(moduleNameProperty)
            .horizontalAlign(HorizontalAlign.FILL)
            .validationOnInput { validateModuleName() }
            .validationOnApply { validateModuleName() }
        }.bottomGap(BottomGap.SMALL)
        row(UIBundle.message("label.project.wizard.new.project.content.root")) {
          val browseDialogTitle = UIBundle.message("label.project.wizard.new.project.content.root.title")
          val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
          textFieldWithBrowseButton(browseDialogTitle, context.project, fileChooserDescriptor) { getPresentablePath(it.path) }
            .bindText(contentRootProperty.toUiPathProperty())
            .horizontalAlign(HorizontalAlign.FILL)
            .validationOnApply { validateContentRoot() }
            .apply {
              component.textField.addKeyListener(object : KeyListener {
                override fun keyTyped(e: KeyEvent?) {}
                override fun keyPressed(e: KeyEvent?) {
                  userDefinedContentRoot = true
                }

                override fun keyReleased(e: KeyEvent?) {}
              })
            }
        }.bottomGap(BottomGap.SMALL)
        row(UIBundle.message("label.project.wizard.new.project.module.file.location")) {
          val browseDialogTitle = UIBundle.message("label.project.wizard.new.project.module.file.location.title")
          val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
          textFieldWithBrowseButton(browseDialogTitle, context.project, fileChooserDescriptor) { getPresentablePath(it.path) }
            .bindText(moduleFileLocationProperty.toUiPathProperty())
            .horizontalAlign(HorizontalAlign.FILL)
            .validationOnApply { validateModuleFileLocation() }
            .apply {
              component.textField.addKeyListener(object : KeyListener {
                override fun keyTyped(e: KeyEvent?) {}
                override fun keyPressed(e: KeyEvent?) {
                  userDefinedModuleFileLocation = true
                }

                override fun keyReleased(e: KeyEvent?) {}
              })
            }
        }.bottomGap(BottomGap.SMALL)

        customAdditionalOptions()
      }.topGap(TopGap.MEDIUM)
    }
  }

  open fun Panel.customOptions() {}

  open fun Panel.customAdditionalOptions() {}

  private fun ValidationInfoBuilder.validateModuleName(): ValidationInfo? {
    if (moduleName.isEmpty()) return error(JavaUiBundle.message("module.name.location.dialog.message.enter.module.name"))

    // Name uniqueness
    val project = context.project
    if (project != null) {
      val model = ProjectStructureConfigurable.getInstance(project)?.context?.modulesConfigurator?.moduleModel
      val module = when (model) {
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
}