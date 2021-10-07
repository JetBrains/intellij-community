// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.io.systemIndependentPath
import javax.swing.JLabel

class WebTemplateProjectWizardStep<T>(val parent: NewProjectWizardBaseStep,
                                      val template: WebProjectTemplate<T>) : AbstractNewProjectWizardStep(parent) {
  val peer: NotNullLazyValue<ProjectGeneratorPeer<T>> = template.createLazyPeer()

  override fun setupUI(builder: Panel) {
    peer.value.buildUI(PanelBuilderSettingsStep(parent.context, builder))

    val errorLabel = JLabel("")
    errorLabel.foreground = JBColor.RED

    //legacy error handling
    builder.row {
      cell(errorLabel).horizontalAlign(HorizontalAlign.FILL).validationOnApply { 
        peer.value.validate()
      }
    }
    
    peer.value.addSettingsListener { 
      val validate = peer.value.validate()
      errorLabel.text = validate?.message ?: ""
    }
  }

  override fun setupProject(project: Project) {
    val builder = template.createModuleBuilder()
    builder.moduleFilePath = parent.projectPath.systemIndependentPath
    builder.contentEntryPath = parent.projectPath.systemIndependentPath
    builder.name = parent.name
    builder.commitModule(project, null)
  }
}