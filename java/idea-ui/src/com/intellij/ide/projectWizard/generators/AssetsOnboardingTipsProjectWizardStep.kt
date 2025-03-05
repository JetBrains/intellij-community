// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.ide.projectWizard.generators.prepareOnboardingTips as prepareOnboardingTipsImpl

@Deprecated("Use AssetsOnboardingTips util instead")
abstract class AssetsOnboardingTipsProjectWizardStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {
  protected fun rawShortcut(shortcut: String) = AssetsOnboardingTips.rawShortcut(shortcut)
  protected fun shortcut(actionId: String) = AssetsOnboardingTips.shortcut(actionId)
  protected fun icon(allIconsId: String) = AssetsOnboardingTips.icon(allIconsId)
  protected fun shouldRenderOnboardingTips(): Boolean = AssetsOnboardingTips.shouldRenderOnboardingTips()
  protected fun prepareOnboardingTips(project: Project, templateWithoutTips: String, fileName: String, breakpointSelector: (CharSequence) -> Int?) =
    prepareOnboardingTipsImpl(project, fileName, breakpointSelector)
}