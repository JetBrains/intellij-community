// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.INTELLIJ
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel

class IntelliJJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {

  override val name = INTELLIJ

  override val ordinal = 0

  override fun createStep(parent: JavaNewProjectWizard.Step): NewProjectWizardStep =
    Step(parent)
      .nextStep(::AssetsStep)

  class Step(parent: JavaNewProjectWizard.Step) :
    IntelliJNewProjectWizardStep<JavaNewProjectWizard.Step>(parent),
    BuildSystemJavaNewProjectWizardData by parent,
    IntelliJJavaNewProjectWizardData {

    override fun setupSettingsUI(builder: Panel) {
      setupJavaSdkUI(builder)
      setupSampleCodeUI(builder)
    }

    override fun setupAdvancedSettingsUI(builder: Panel) {
      setupModuleNameUI(builder)
      setupModuleContentRootUI(builder)
      setupModuleFileLocationUI(builder)
    }

    override fun setupProject(project: Project) {
      setupProject(project, JavaModuleBuilder())
    }

    init {
      data.putUserData(IntelliJJavaNewProjectWizardData.KEY, this)
    }
  }

  private class AssetsStep(
    private val parent: Step
  ) : AssetsNewProjectWizardStep(parent) {

    override fun setupAssets(project: Project) {
      setOutputDirectory(parent.contentRoot)

      if (context.isCreatingNewProject) {
        addAssets(StandardAssetsProvider().getIntelliJIgnoreAssets())
      }
      if (parent.addSampleCode) {
        withJavaSampleCodeAsset(project, "src", jdkIntent = parent.jdkIntent)
      }
    }
  }
}