// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.NewProjectStep.Settings
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardMultiStep
import com.intellij.openapi.util.Key
import com.intellij.ui.UIBundle

class NewProjectStep(context: WizardContext) : NewModuleStep(context) {

  override val steps = super.steps + Step(context)

  class Step(context: WizardContext) : NewProjectWizardMultiStep<Settings>(context, NewProjectWizard.EP_NAME) {
    override val label = UIBundle.message("label.project.wizard.new.project.language")

    override val settings = Settings(context)
  }

  class Settings(context: WizardContext) : NewProjectWizardMultiStep.Settings<Settings>(KEY, context) {
    companion object {
      val KEY = Key.create<Settings>(Settings::class.java.name)
    }
  }
}
