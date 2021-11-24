// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.wizard.*

class JavaNewProjectWizard : LanguageNewProjectWizard {
  override val name: String = "Java"

  override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent)

  class Step(parent: NewProjectWizardLanguageStep) :
    AbstractNewProjectWizardMultiStep<Step>(parent, BuildSystemJavaNewProjectWizard.EP_NAME),
    NewProjectWizardLanguageData by parent,
    NewProjectWizardBuildSystemData {

    override val self = this

    override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")

    override val buildSystemProperty by ::stepProperty
    override val buildSystem by ::step

    init {
      data.putUserData(NewProjectWizardBuildSystemData.KEY, this)
    }
  }
}
