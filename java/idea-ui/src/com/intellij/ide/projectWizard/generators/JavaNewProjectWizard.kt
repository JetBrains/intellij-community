// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.wizard.*
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.ide.wizard.NewProjectWizardLanguageStep

class JavaNewProjectWizard : NewProjectWizard {
  override val name: String = "Java"

  override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent, SdkStep(parent))

  class Step(
    parent: NewProjectWizardLanguageStep,
    override val commonStep: SdkStep
  ) : AbstractNewProjectWizardMultiStep<NewProjectWizardLanguageStep, Step>(parent, JavaBuildSystemType.EP_NAME),
      NewProjectWizardBuildSystemData,
      NewProjectWizardLanguageData by parent,
      NewProjectWizardSdkData by commonStep {

    override val self = this

    override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")

    override val buildSystemProperty by ::stepProperty
    override val buildSystem by ::step
  }

  class SdkStep(parent: NewProjectWizardLanguageStep) : AbstractNewProjectWizardSdkStep(parent) {
    override fun sdkTypeFilter(type: SdkTypeId): Boolean {
      return type is JavaSdkType && type !is DependentSdkType
    }
  }
}
