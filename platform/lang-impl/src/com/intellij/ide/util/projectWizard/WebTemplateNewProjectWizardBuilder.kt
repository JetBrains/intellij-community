// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard

import com.intellij.ide.wizard.*
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.WebModuleBuilder
import com.intellij.openapi.module.WebModuleTypeBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.UIBundle
import javax.swing.Icon

abstract class WebTemplateNewProjectWizardBuilderBase : AbstractNewProjectWizardBuilder() {
  override fun createStep(context: WizardContext): NewProjectWizardStep {
    return object : GitNewProjectWizardStep(context) {
      override fun showGitRepositoryCheckbox(): Boolean {
        return false
      }
    }.chain { createTemplateStep(it) }
  }

  protected abstract fun createTemplateStep(parent: GitNewProjectWizardStep): NewProjectWizardStep

  override fun getIgnoredSteps(): List<Class<out ModuleWizardStep>> {
    try {
      @Suppress("UNCHECKED_CAST")
      return listOf(Class.forName("com.intellij.ide.projectWizard.ProjectSettingsStep") as Class<ModuleWizardStep>)
    }
    catch (e: ClassNotFoundException) {
    }

    return emptyList()
  }

  override fun isAvailable(): Boolean {
    return Experiments.getInstance().isFeatureEnabled("new.project.wizard")
  }

  override fun getModuleType(): ModuleType<*> = WebModuleTypeBase.getInstance()
  override fun getGroupName() = WebModuleBuilder.GROUP_NAME
}


open class WebTemplateNewProjectWizardBuilder(private val template: WebProjectTemplate<*>) : WebTemplateNewProjectWizardBuilderBase() {
  override fun getBuilderId(): String? = template.javaClass.name
  override fun createTemplateStep(parent: GitNewProjectWizardStep): NewProjectWizardStep =
    WebTemplateProjectWizardStep(parent, template)

  override fun getPresentableName() = StringUtil.capitalizeWords(template.name, true)
  override fun getNodeIcon(): Icon = template.icon
}

open class MultiWebTemplateNewProjectWizardBuilder(protected val templates: List<WebProjectTemplate<*>>) : WebTemplateNewProjectWizardBuilderBase() {
  override fun createTemplateStep(parent: GitNewProjectWizardStep): NewProjectWizardStep {
    return object : AbstractNewProjectWizardMultiStepBase(parent) {
      override val label: String
        get() = UIBundle.message("label.project.wizard.new.project.project.type")
      override val steps: Map<String, NewProjectWizardStep>
        get() = templates.associateBy({ it.name }, { WebTemplateProjectWizardStep(parent, it) })
    }
  }

  override fun getBuilderId(): String? = templates.joinToString { it.javaClass.name }
}