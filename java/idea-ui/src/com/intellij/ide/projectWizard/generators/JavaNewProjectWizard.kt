// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.wizard.*
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.ide.wizard.NewProjectStep.Step as LanguageStep

class JavaNewProjectWizard : NewProjectWizard {
  override val name: String = "Java"

  override fun createStep(parent: LanguageStep) = Step(parent, SdkStep(parent))

  class Step(
    parent: LanguageStep,
    sdkStep: SdkStep
  ) : AbstractNewProjectWizardMultiStep<LanguageStep, Step>(parent, JavaBuildSystemType.EP_NAME),
      NewProjectWizardBuildSystemData,
      NewProjectWizardLanguageData by parent,
      NewProjectWizardSdkData by sdkStep {

    override val self = this

    override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")

    override val commonSteps = listOf(sdkStep)

    override val buildSystemProperty by ::stepProperty
    override val buildSystem by ::step
  }

  class SdkStep(parent: LanguageStep) : AbstractNewProjectWizardSdkStep(parent) {
    override fun sdkTypeFilter(type: SdkTypeId): Boolean {
      return type is JavaSdkType && type !is DependentSdkType
    }
  }
}
