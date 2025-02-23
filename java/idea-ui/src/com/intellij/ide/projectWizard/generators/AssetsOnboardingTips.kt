// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.ide.projectWizard.generators

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.wizard.NewProjectOnboardingTips
import com.intellij.ide.wizard.OnboardingTipsInstallationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

object AssetsOnboardingTips {

  fun rawShortcut(shortcut: String): String {
    return """<shortcut raw="$shortcut"/>"""
  }

  fun shortcut(actionId: String): String {
    return """<shortcut actionId="$actionId"/>"""
  }

  fun icon(allIconsId: String): String {
    return """<icon src="$allIconsId"/>"""
  }

  fun shouldRenderOnboardingTips(): Boolean {
    return Registry.`is`("doc.onboarding.tips.render")
  }

  @Deprecated("The onboarding tips generated unconditionally")
  fun proposeToGenerateOnboardingTipsByDefault(): Boolean {
    return RecentProjectsManagerBase.getInstanceEx().getRecentPaths().isEmpty()
  }

  @ApiStatus.Internal
  fun prepareOnboardingTips(project: Project, fileName: String, breakpointSelector: (CharSequence) -> Int?) {
    val onboardingInfo = OnboardingTipsInstallationInfo(fileName, breakpointSelector)
    for (extension in NewProjectOnboardingTips.EP_NAME.extensions) {
      extension.installTips(project, onboardingInfo)
    }
  }
}

@Deprecated("Use AssetsOnboardingTips#prepareOnboardingTips instead")
fun AssetsNewProjectWizardStep.prepareOnboardingTips(project: Project, fileName: String, breakpointSelector: (CharSequence) -> Int?): Unit =
  AssetsOnboardingTips.prepareOnboardingTips(project, fileName, breakpointSelector)