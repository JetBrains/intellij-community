// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard

import com.intellij.ide.util.projectWizard.WebTemplateProjectWizardData.Companion.webTemplateData
import com.intellij.ide.wizard.AbstractNewProjectWizardMultiStepBase
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.openapi.module.WebModuleBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.UIBundle
import java.util.function.Consumer
import javax.swing.Icon

abstract class WebTemplateNewProjectWizardBase : GeneratorNewProjectWizard {
  override val groupName: String = WebModuleBuilder.GROUP_NAME

  override fun createStep(context: WizardContext): NewProjectWizardStep =
    RootNewProjectWizardStep(context)
      .nextStep(::NewProjectWizardBaseStep)
      .nextStep(::createTemplateStep)

  abstract fun createTemplateStep(parent: NewProjectWizardBaseStep): NewProjectWizardStep
}

class WebTemplateNewProjectWizard(val template: WebProjectTemplate<*>) : WebTemplateNewProjectWizardBase() {
  override val id: String = template.id
  override val name: String = StringUtil.capitalizeWords(template.name, true)
  override val icon: Icon = template.icon

  override fun createTemplateStep(parent: NewProjectWizardBaseStep): NewProjectWizardStep =
    WebTemplateProjectWizardStep(parent, template)
}

abstract class MultiWebTemplateNewProjectWizard(protected val templates: List<WebProjectTemplate<*>>) : WebTemplateNewProjectWizardBase() {
  override fun createTemplateStep(parent: NewProjectWizardBaseStep): NewProjectWizardStep {
    return object : AbstractNewProjectWizardMultiStepBase(parent) {
      override val label: String
        get() = UIBundle.message("label.project.wizard.new.project.project.type")

      override fun initSteps() = templates.associateBy({ it.name }, { WebTemplateProjectWizardStep(parent, it) })
    }
  }
}

fun WizardContext.switchToRequested(placeId: String, consumer: Consumer<ProjectGeneratorPeer<*>>) {
  requestSwitchTo(placeId) { step ->
    when (step) {
      is AbstractNewProjectDialog.ProjectStepPeerHolder -> {
        consumer.accept(step.peer)
      }
      is NewProjectWizardStep -> {
        step.webTemplateData?.let {
          consumer.accept(it.peer.value)
        }
      }
      else -> throw UnsupportedOperationException("Unsupported step type: ${step.javaClass}")
    }
  }
}