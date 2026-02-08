// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Point

internal class SettingsPopupStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val popup = NewUiOnboardingUtil.createSettingsEntryPointPopup(project, disposable) ?: return null
    val actionsList = UIUtil.findComponentOfType(popup.content, JBList::class.java) ?: return null
    val pluginsActionBounds = NewUiOnboardingUtil.findActionItemBounds(actionsList, "WelcomeScreen.Plugins") ?: return null
    val builder = GotItComponentBuilder(NewUsersOnboardingBundle.message("settings.popup.step.text"))
      .withHeader(NewUsersOnboardingBundle.message("settings.popup.step.header"))

    val point = Point(JBUI.scale(-4), pluginsActionBounds.y + pluginsActionBounds.height / 2)
    val relativePoint = NewUiOnboardingUtil.convertPointToFrame(project, actionsList, point) ?: return null
    return NewUiOnboardingStepData(builder, relativePoint, Balloon.Position.atLeft)
  }
}