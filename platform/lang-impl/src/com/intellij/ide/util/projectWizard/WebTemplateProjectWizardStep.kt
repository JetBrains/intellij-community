// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.WebModuleBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import java.util.function.Consumer
import javax.swing.JLabel

class WebTemplateProjectWizardStep<T>(
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
    webModuleBuilder().commitModule(project, null)
  }

  private fun webModuleBuilder(): WebModuleBuilder<T> {
    return WebModuleBuilder(template, peer).apply {
      moduleFilePath = "${parent.path}/${parent.name}"
      contentEntryPath = "${parent.path}/${parent.name}"
      name = parent.name
    }
  }

  override fun createModuleConfigurator(): Consumer<Module>? {
    return webModuleBuilder().createModuleConfigurator()
  }

  init {
    data.putUserData(WebTemplateProjectWizardData.KEY, this)
  }
}