// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import com.intellij.ide.projectWizard.generators.withJavaSampleCodeAsset as withJavaSampleCodeAssetImpl
import com.intellij.ide.projectWizard.generators.prepareJavaSampleOnboardingTips as prepareJavaSampleOnboardingTipsImpl

@Deprecated("Use AssetsJava util instead")
abstract class AssetsJavaNewProjectWizardStep(parent: NewProjectWizardStep) : AssetsOnboardingTipsProjectWizardStep(parent) {

  fun withJavaSampleCodeAsset(sourceRootPath: String, aPackage: String, generateOnboardingTips: Boolean) =
    withJavaSampleCodeAssetImpl(sourceRootPath, aPackage.nullize(), generateOnboardingTips)

  @ScheduledForRemoval
  @Deprecated("Use prepareOnboardingTips and it should be called before wizard project setup")
  fun prepareTipsInEditor(project: Project) = Unit

  fun prepareOnboardingTips(project: Project) =
    prepareJavaSampleOnboardingTipsImpl(project)

  companion object {

    @Deprecated("Use AssetsJava util instead")
    fun createJavaSourcePath(sourceRootPath: String, aPackage: String, fileName: String) =
      AssetsJava.getJavaSampleSourcePath(sourceRootPath, aPackage.nullize(), fileName)

    @Deprecated("Use AssetsOnboardingTips util instead")
    fun proposeToGenerateOnboardingTipsByDefault() =
      AssetsOnboardingTips.proposeToGenerateOnboardingTipsByDefault()
  }
}