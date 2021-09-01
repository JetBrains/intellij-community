// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import java.nio.file.Paths

class IntelliJJavaBuildSystemType : JavaBuildSystemType {
  override val name = "IntelliJ"

  override fun createStep(context: WizardContext) = Step(context)

  class Step(context: WizardContext) : NewProjectWizardStep(context) {
    var moduleName: String = ""
    var contentRoot: String = ""
    var moduleFileLocation: String = ""

    override fun setupUI(builder: RowBuilder) {
      with(builder) {
        hideableRow(UIBundle.message("label.project.wizard.new.project.advanced.settings")) {
          row(UIBundle.message("label.project.wizard.new.project.module.name")) {
            textField(::moduleName)
          }
          row(UIBundle.message("label.project.wizard.new.project.content.root")) {
            textFieldWithBrowseButton(::contentRoot,
              UIBundle.message("label.project.wizard.new.project.content.root.title"), context.project,
              FileChooserDescriptorFactory.createSingleFolderDescriptor())
          }
          row(UIBundle.message("label.project.wizard.new.project.module.file.location")) {
            textFieldWithBrowseButton(::moduleFileLocation,
              UIBundle.message("label.project.wizard.new.project.module.file.location.title"), context.project,
              FileChooserDescriptorFactory.createSingleFolderDescriptor())
          }
        }
      }
    }

    override fun setupProject(project: Project) {
      val builder = JavaModuleBuilder()
      val moduleFile = Paths.get(moduleFileLocation, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION)

      builder.name = moduleName
      builder.moduleFilePath = FileUtil.toSystemDependentName(moduleFile.toString())
      builder.contentEntryPath = FileUtil.toSystemDependentName(contentRoot)
      builder.moduleJdk = JavaNewProjectWizard.SdkStep.getSdk(context)

      builder.commit(project)
    }
  }
}