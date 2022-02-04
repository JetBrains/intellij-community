// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.util.projectWizard.ProjectWizardUtil
import com.intellij.ide.wizard.*
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.io.File

abstract class IntelliJNewProjectWizardStep<ParentStep>(val parent: ParentStep) :
  AbstractNewProjectWizardStep(parent), IntelliJNewProjectWizardData
  where ParentStep : NewProjectWizardStep,
        ParentStep : NewProjectWizardBaseData {

  private val defaultBaseDir: String
    get() = when (context.isCreatingNewProject) {
      true -> parent.path
      false -> context.project?.guessProjectDir()?.path ?: ""
    }

  private val pathFromParent = { "${parent.path}/${parent.name}" }
  private val pathFromModuleName = {
    val path = defaultBaseDir
    when (moduleName != parent.name) {
      true -> "$path/${parent.name}/$moduleName"
      false -> "$path/${parent.name}"
    }
  }

  val sdkProperty = propertyGraph.property<Sdk?>(null)
  val moduleNameProperty = propertyGraph.lazyProperty { parent.name }
  val contentRootProperty = propertyGraph.lazyProperty(pathFromParent)
  val moduleFileLocationProperty = propertyGraph.lazyProperty(pathFromParent)
  val addSampleCodeProperty = propertyGraph.property(false)

  final override var sdk by sdkProperty
  final override var moduleName by moduleNameProperty
  final override var contentRoot by contentRootProperty
  final override var moduleFileLocation by moduleFileLocationProperty
  final override var addSampleCode by addSampleCodeProperty

  private var userDefinedContentRoot: Boolean = false
  private var userDefinedModuleFileLocation: Boolean = false

  init {
    moduleNameProperty.dependsOn(parent.nameProperty) { parent.name }

    contentRootProperty.dependsOn(parent.pathProperty, pathFromParent)
    contentRootProperty.dependsOn(parent.nameProperty, pathFromParent)
    moduleFileLocationProperty.dependsOn(parent.pathProperty, pathFromParent)
    moduleFileLocationProperty.dependsOn(parent.nameProperty, pathFromParent)

    contentRootProperty.dependsOn(moduleNameProperty, pathFromModuleName)
    moduleFileLocationProperty.dependsOn(moduleNameProperty, pathFromModuleName)

    moduleNameProperty.dependsOn(contentRootProperty) { File(contentRoot).name }
    moduleFileLocationProperty.dependsOn(contentRootProperty) { contentRoot }
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
        if (context.isCreatingNewProject) {
          row(UIBundle.message("label.project.wizard.new.project.module.name")) {
            textField()
              .bindText(moduleNameProperty)
              .horizontalAlign(HorizontalAlign.FILL)
              .validationOnInput { validateModuleName() }
              .validationOnApply { validateModuleName() }
          }.bottomGap(BottomGap.SMALL)
          row(UIBundle.message("label.project.wizard.new.project.content.root")) {
            textFieldWithBrowseButton(UIBundle.message("label.project.wizard.new.project.content.root.title"), context.project,
              FileChooserDescriptorFactory.createSingleFolderDescriptor()) { file: VirtualFile -> getPresentablePath(file.path) }
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
        }
        row(UIBundle.message("label.project.wizard.new.project.module.file.location")) {
          textFieldWithBrowseButton(UIBundle.message("label.project.wizard.new.project.module.file.location.title"), context.project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()) { file: VirtualFile -> getPresentablePath(file.path) }
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