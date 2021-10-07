// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard

import com.intellij.ide.wizard.AbstractNewProjectWizardBuilder
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.chain
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.WebModuleBuilder
import com.intellij.openapi.module.WebModuleTypeBase
import javax.swing.Icon

open class WebTemplateNewProjectWizardBuilder<T>(val template: WebProjectTemplate<T>) : AbstractNewProjectWizardBuilder() {
  override fun createStep(context: WizardContext): NewProjectWizardStep {
    return object : NewProjectWizardBaseStep(context) {
      override fun showGitRepositoryCheckbox(): Boolean {
        return false
      }
    }.chain { WebTemplateProjectWizardStep(it, template) }
  }

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
  override fun getPresentableName() = template.name
  override fun getBuilderId(): String? = template.javaClass.name
  override fun getNodeIcon(): Icon = template.icon
}