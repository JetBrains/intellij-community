// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStepSettings
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import java.nio.file.Paths

class IntelliJJavaBuildSystemType : JavaBuildSystemType {
  override val name = "IntelliJ"

  override fun createStep(context: WizardContext) = Step(context)

  class Step(private val context: WizardContext) : NewProjectWizardStep<Settings> {
    override val settings = Settings(context)

    override fun setupUI(builder: RowBuilder) {
      with(builder) {
        hideableRow(UIBundle.message("label.project.wizard.new.project.advanced.settings")) {
          row(UIBundle.message("label.project.wizard.new.project.module.name")) {
            textField(settings::moduleName)
          }
          row(UIBundle.message("label.project.wizard.new.project.content.root")) {
            textFieldWithBrowseButton(settings::contentRoot,
              UIBundle.message("label.project.wizard.new.project.content.root.title"), context.project,
              FileChooserDescriptorFactory.createSingleFolderDescriptor())
          }
          row(UIBundle.message("label.project.wizard.new.project.module.file.location")) {
            textFieldWithBrowseButton(settings::moduleFileLocation,
              UIBundle.message("label.project.wizard.new.project.module.file.location.title"), context.project,
              FileChooserDescriptorFactory.createSingleFolderDescriptor())
          }
        }
      }
    }

    override fun setupProject(project: Project) {
      val builder = JavaModuleBuilder()
      val moduleFile = Paths.get(settings.moduleFileLocation, settings.moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION)

      builder.name = settings.moduleName
      builder.moduleFilePath = FileUtil.toSystemDependentName(moduleFile.toString())
      builder.contentEntryPath = FileUtil.toSystemDependentName(settings.contentRoot)
      builder.moduleJdk = JavaNewProjectWizard.SdkSettings.getSdk(context)

      builder.commit(project)
    }
  }

  class Settings(context: WizardContext) : NewProjectWizardStepSettings<Settings>(KEY, context) {
    var moduleName: String = ""
    var contentRoot: String = ""
    var moduleFileLocation: String = ""

    companion object {
      val KEY = Key.create<Settings>(Settings::class.java.name)
    }
  }
}