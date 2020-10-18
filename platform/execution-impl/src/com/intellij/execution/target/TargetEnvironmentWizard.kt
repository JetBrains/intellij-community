// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.ide.wizard.AbstractWizardEx
import com.intellij.ide.wizard.AbstractWizardStepEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts

class TargetEnvironmentWizard(project: Project,
                              @NlsContexts.DialogTitle title: String,
                              val subject: TargetEnvironmentConfiguration,
                              steps: List<AbstractWizardStepEx>)
  : AbstractWizardEx(title, project, steps) {

  override fun getHelpId(): String = "reference.remote.target.wizard.${subject.typeId}"

  companion object {
    @JvmStatic
    fun <C : TargetEnvironmentConfiguration> createWizard(
      project: Project, targetType: TargetEnvironmentType<C>, runtimeType: LanguageRuntimeType<*>?
    ): TargetEnvironmentWizard? {

      if (!targetType.providesNewWizard(project, runtimeType)) {
        return null
      }

      val config = targetType.createDefaultConfig()
      val steps = targetType.createStepsForNewWizard(project, config, runtimeType) ?: return null

      return TargetEnvironmentWizard(
        project, ExecutionBundle.message("run.on.targets.wizard.title.new.target"), config, steps)
    }
  }
}