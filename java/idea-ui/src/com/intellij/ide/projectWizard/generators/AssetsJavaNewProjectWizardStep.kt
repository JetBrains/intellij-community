// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import com.intellij.ide.projectWizard.generators.prepareJavaSampleOnboardingTips as prepareJavaSampleOnboardingTipsImpl
import com.intellij.ide.projectWizard.generators.withJavaSampleCodeAsset as withJavaSampleCodeAssetImpl

@Deprecated("Use AssetsJava util instead")
abstract class AssetsJavaNewProjectWizardStep(parent: NewProjectWizardStep) : AssetsOnboardingTipsProjectWizardStep(parent) {

  fun withJavaSampleCodeAsset(sourceRootPath: String, aPackage: String, generateOnboardingTips: Boolean) =
    withJavaSampleCodeAssetImpl(sourceRootPath, aPackage.nullize(), generateOnboardingTips)

  fun prepareOnboardingTips(project: Project) =
    prepareJavaSampleOnboardingTipsImpl(project)

  companion object {
    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use AssetsJava util instead")
    fun createJavaSourcePath(sourceRootPath: String, aPackage: String, fileName: String) =
      AssetsJava.getJavaSampleSourcePath(sourceRootPath, aPackage.nullize(), fileName)

    @Deprecated("Use AssetsOnboardingTips util instead")
    fun proposeToGenerateOnboardingTipsByDefault() =
      AssetsOnboardingTips.proposeToGenerateOnboardingTipsByDefault()
  }
}