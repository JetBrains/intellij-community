// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard

import com.intellij.ide.wizard.*
import com.intellij.openapi.module.WebModuleBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.UIBundle
import javax.swing.Icon

abstract class WebTemplateNewProjectWizardBase : GeneratorNewProjectWizard {
  override val groupName: String = WebModuleBuilder.GROUP_NAME

  override fun createStep(context: WizardContext): NewProjectWizardStep =
    RootNewProjectWizardStep(context).chain(::NewProjectWizardBaseStep, ::createTemplateStep)

  abstract fun createTemplateStep(parent: NewProjectWizardBaseStep): NewProjectWizardStep
}

class WebTemplateNewProjectWizard(val template: WebProjectTemplate<*>) : WebTemplateNewProjectWizardBase() {
  override val id: String = template.javaClass.name
  override val name: String = StringUtil.capitalizeWords(template.name, true)
  override val icon: Icon = template.icon

  override fun createTemplateStep(parent: NewProjectWizardBaseStep): NewProjectWizardStep =
    WebTemplateProjectWizardStep(parent, template)
}

abstract class MultiWebTemplateNewProjectWizard(protected val templates: List<WebProjectTemplate<*>>) : WebTemplateNewProjectWizardBase() {
  override val id: String = templates.joinToString { it.javaClass.name }

  override fun createTemplateStep(parent: NewProjectWizardBaseStep): NewProjectWizardStep {
    return object : AbstractNewProjectWizardMultiStepBase(parent) {
      override val label: String
        get() = UIBundle.message("label.project.wizard.new.project.project.type")

      override fun initSteps() = templates.associateBy({ it.name }, { WebTemplateProjectWizardStep(parent, it) })
    }
  }
}
