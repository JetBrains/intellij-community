// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardMultiStep
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.util.Key

class JavaNewProjectWizard : NewProjectWizard {
  override val name: String = "Java"

  override fun createStep(context: WizardContext) = Step(context)

  class Step(context: WizardContext) : NewProjectWizardMultiStep(context, JavaBuildSystemType.EP_NAME) {
    override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")

    override val commonSteps = listOf(SdkStep(context))
  }

  class SdkStep(context: WizardContext) : SdkNewProjectWizardStep(context) {
    override fun sdkTypeFilter(type: SdkTypeId): Boolean {
      return type is JavaSdkType && type !is DependentSdkType
    }

    init {
      KEY.set(context, this)
    }

    companion object {
      val KEY = Key.create<SdkStep>(SdkStep::class.java.name)

      fun getSdkComboBox(context: WizardContext) = KEY.get(context).sdkComboBox
      fun getSdk(context: WizardContext) = KEY.get(context).sdk
    }
  }
}
