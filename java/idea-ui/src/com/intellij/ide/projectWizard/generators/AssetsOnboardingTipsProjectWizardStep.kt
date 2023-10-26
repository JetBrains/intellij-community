// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.wizard.NewProjectOnboardingTips
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.OnboardingTipsInstallationInfo
import com.intellij.ide.wizard.whenProjectCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class AssetsOnboardingTipsProjectWizardStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {
  protected fun rawShortcut(shortcut: String) = """<shortcut raw="$shortcut"/>"""
  protected fun shortcut(actionId: String) = """<shortcut actionId="$actionId"/>"""
  protected fun icon(allIconsId: String) = """<icon src="$allIconsId"/>"""

  protected fun shouldRenderOnboardingTips(): Boolean = Registry.`is`("doc.onboarding.tips.render")

  protected fun prepareOnboardingTips(project: Project, templateWithoutTips: String, breakpointSelector: (CharSequence) -> Int?) = whenProjectCreated(project) {
    val templateManager = FileTemplateManager.getDefaultInstance()
    val properties = getTemplateProperties()
    val defaultProperties = templateManager.defaultProperties
    val template = templateManager.getInternalTemplate(templateWithoutTips)
    val simpleSampleText = template.getText(defaultProperties + properties)
    val onboardingInfo = OnboardingTipsInstallationInfo(simpleSampleText, breakpointSelector)
    for (extension in NewProjectOnboardingTips.EP_NAME.extensions) {
      extension.installTips(project, onboardingInfo)
    }
  }
}