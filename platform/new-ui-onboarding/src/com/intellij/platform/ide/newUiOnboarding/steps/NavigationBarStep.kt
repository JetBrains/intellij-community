// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBPoint

class NavigationBarStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val statusBar = WindowManager.getInstance().getStatusBar(project).component ?: return null
    val builder = GotItComponentBuilder(NewUiOnboardingBundle.message("navigation.bar.step.text"))
      .withHeader(NewUiOnboardingBundle.message("navigation.bar.step.header"))

    val relativePoint = RelativePoint(statusBar, JBPoint(80, -2))
    return NewUiOnboardingStepData(builder, relativePoint, Balloon.Position.above)
  }

  override fun isAvailable(): Boolean {
    val settings = UISettings.getInstance()
    return settings.showStatusBar && settings.showNavigationBarInBottom
  }
}