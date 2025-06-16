// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.setupProjectFromBuilder
import com.intellij.openapi.module.WebModuleBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import java.nio.file.Path
import javax.swing.JLabel

open class WebTemplateProjectWizardStep<T>(
  val parent: NewProjectWizardBaseStep,
  val template: WebProjectTemplate<T>
) : AbstractNewProjectWizardStep(parent), WebTemplateProjectWizardData<T> {

  override val peer: NotNullLazyValue<ProjectGeneratorPeer<T>> = template.createLazyPeer()

  override fun setupUI(builder: Panel) {
    peer.value.buildUI(PanelBuilderSettingsStep(parent.context, builder, parent))

    val errorLabel = JLabel("")
    errorLabel.foreground = JBColor.RED

    //legacy error handling
    builder.row {
      cell(errorLabel)
        .align(AlignX.FILL)
        .validationRequestor { validate ->
          peer.value.addSettingsListener {
            validate()
          }
        }
        .validationInfo {
          val validation = peer.value.validate()
          errorLabel.text = validation?.message ?: ""
          return@validationInfo validation
        }
    }
  }

  override fun setupProject(project: Project) {
    val moduleName = parent.name
    val projectPath = Path.of(parent.path, moduleName)
    val builder = WebModuleBuilder(template, peer)
    builder.contentEntryPath = projectPath.toString()
    builder.moduleFilePath = projectPath.resolve(moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION).toString()
    builder.name = moduleName
    setupProjectFromBuilder(project, builder)
  }

  init {
    data.putUserData(WebTemplateProjectWizardData.KEY, this)
  }
}