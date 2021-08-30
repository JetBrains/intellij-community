// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardMultiStep
import com.intellij.ui.UIBundle

class NewProjectStep(context: WizardContext) : NewModuleStep(context) {

  override val steps = super.steps + Step(context)

  class Step(context: WizardContext) : NewProjectWizardMultiStep(context, NewProjectWizard.EP_NAME) {
    override val label = UIBundle.message("label.project.wizard.new.project.language")
  }
}
