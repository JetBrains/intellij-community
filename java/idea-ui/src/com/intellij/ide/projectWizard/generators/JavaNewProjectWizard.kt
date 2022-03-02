// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemFinished
import com.intellij.ide.wizard.*
import com.intellij.openapi.project.Project

class JavaNewProjectWizard : LanguageNewProjectWizard {
  override val name: String = JAVA
  override val ordinal = 0

  override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent)

  class Step(parent: NewProjectWizardLanguageStep) :
    AbstractNewProjectWizardMultiStep<Step, BuildSystemJavaNewProjectWizard>(parent, BuildSystemJavaNewProjectWizard.EP_NAME),
    LanguageNewProjectWizardData by parent,
    BuildSystemJavaNewProjectWizardData {

    override val self = this

    override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")

    override val buildSystemProperty by ::stepProperty
    override var buildSystem by ::step


    override fun setupProject(project: Project) {
      super.setupProject(project)

      logBuildSystemFinished()
    }

    init {
      data.putUserData(BuildSystemJavaNewProjectWizardData.KEY, this)

      buildSystemProperty.afterChange { logBuildSystemChanged() }
    }
  }

  companion object {
    const val JAVA = "Java"
  }
}
