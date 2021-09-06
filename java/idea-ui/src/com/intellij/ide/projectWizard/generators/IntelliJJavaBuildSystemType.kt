// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.wizard.AbstractNewProjectWizardChildStep
import com.intellij.ide.util.projectWizard.ProjectWizardUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.nio.file.Paths

class IntelliJJavaBuildSystemType : JavaBuildSystemType {
  override val name = "IntelliJ"

  override fun createStep(parent: JavaNewProjectWizard.Step) = Step(parent)

  class Step(private val parent: JavaNewProjectWizard.Step) : AbstractNewProjectWizardChildStep<JavaNewProjectWizard.Step>(parent) {
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

    private val moduleNameProperty = propertyGraph.graphProperty { parent.name }
    private val contentRootProperty = propertyGraph.graphProperty(pathFromParent)
    private val moduleFileLocationProperty = propertyGraph.graphProperty(pathFromParent)

    private var moduleName by moduleNameProperty
    private var contentRoot by contentRootProperty
    private var moduleFileLocation by moduleFileLocationProperty

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
    }

    override fun setupUI(builder: LayoutBuilder) {
      with(builder) {
        hideableRow(UIBundle.message("label.project.wizard.new.project.advanced.settings")) {
          row(UIBundle.message("label.project.wizard.new.project.module.name")) {
            textField(moduleNameProperty)
              .withValidationOnInput { validateModuleName() }
              .withValidationOnApply { validateModuleName() }
          }
          row(UIBundle.message("label.project.wizard.new.project.content.root")) {
            textFieldWithBrowseButton(contentRootProperty,
              UIBundle.message("label.project.wizard.new.project.content.root.title"), context.project,
              FileChooserDescriptorFactory.createSingleFolderDescriptor())
              .withValidationOnApply { validateContentRoot() }
              .apply {
                component.textField.addKeyListener(object : KeyListener {
                  override fun keyTyped(e: KeyEvent?) {}
                  override fun keyPressed(e: KeyEvent?) { userDefinedContentRoot = true }
                  override fun keyReleased(e: KeyEvent?) {}
                })
              }
          }
          row(UIBundle.message("label.project.wizard.new.project.module.file.location")) {
            textFieldWithBrowseButton(moduleFileLocationProperty,
              UIBundle.message("label.project.wizard.new.project.module.file.location.title"), context.project,
              FileChooserDescriptorFactory.createSingleFolderDescriptor())
              .withValidationOnApply { validateModuleFileLocation() }
              .apply {
                component.textField.addKeyListener(object : KeyListener {
                  override fun keyTyped(e: KeyEvent?) {}
                  override fun keyPressed(e: KeyEvent?) { userDefinedModuleFileLocation = true }
                  override fun keyReleased(e: KeyEvent?) {}
                })
              }
          }
        }
      }
    }

    private fun ValidationInfoBuilder.validateModuleName(): ValidationInfo? {
      if (moduleName.isEmpty()) return error(JavaUiBundle.message("module.name.location.dialog.message.enter.module.name"))

      // Name uniqueness
      val project = context.project
      if (project != null) {
        val model = ProjectStructureConfigurable.getInstance(project)?.context?.modulesConfigurator?.moduleModel
        val module = when(model) {
          null -> ModuleManager.getInstance(project).findModuleByName(moduleName)
          else -> model.findModuleByName(moduleName)
        }
        if (module != null) return error(JavaUiBundle.message("module.name.location.dialog.message.module.already.exist.in.project", moduleName))
      }
      return null;
    }

    private fun ValidationInfoBuilder.validateContentRoot(): ValidationInfo? {
      if (!ProjectWizardUtil.createDirectoryIfNotExists(JavaUiBundle.message("directory.module.content.root"), contentRoot, userDefinedContentRoot))
        return error(JavaUiBundle.message("module.name.location.dialog.message.error.content.root", contentRoot))
      return null
    }

    private fun ValidationInfoBuilder.validateModuleFileLocation(): ValidationInfo? {
      if (moduleFileLocation.isEmpty())
        return error(JavaUiBundle.message("module.name.location.dialog.message.enter.module.file.location"))
      if (!ProjectWizardUtil.createDirectoryIfNotExists(JavaUiBundle.message("directory.module.file"), moduleFileLocation, userDefinedModuleFileLocation))
        return error(JavaUiBundle.message("module.name.location.dialog.message.error.module.file.location", moduleFileLocation))
      return null
    }

    override fun setupProject(project: Project) {
      val builder = JavaModuleBuilder()
      val moduleFile = Paths.get(moduleFileLocation, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION)

      builder.name = moduleName
      builder.moduleFilePath = FileUtil.toSystemDependentName(moduleFile.toString())
      builder.contentEntryPath = FileUtil.toSystemDependentName(contentRoot)
      builder.moduleJdk = parentStep.sdk

      builder.commit(project)
    }
  }
}