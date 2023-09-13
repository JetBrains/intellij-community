// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.ide.ui.UISettings
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil.dropMnemonic
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBPoint
import com.intellij.util.ui.JBUI

open class NavigationBarStep : NewUiOnboardingStep {
  protected open val stepText: String
    get() {
      val menuPath = ActionsBundle.message("group.ViewMenu.text").dropMnemonic() +
                     " | " + ActionsBundle.message("group.ViewAppearanceGroup.text").dropMnemonic() +
                     " | " + ActionsBundle.message("group.NavbarLocationGroup.text")
      return NewUiOnboardingBundle.message("navigation.bar.step.text", menuPath)
    }

  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val statusBar = WindowManager.getInstance().getStatusBar(project).component ?: return null
    val builder = GotItComponentBuilder(stepText)
      .withHeader(NewUiOnboardingBundle.message("navigation.bar.step.header"))
      .withMaxWidth(JBUI.scale(300))  // make the text fit in three lines

    val relativePoint = RelativePoint(statusBar, JBPoint(80, -2))
    return NewUiOnboardingStepData(builder, relativePoint, Balloon.Position.above)
  }

  override fun isAvailable(): Boolean {
    val settings = UISettings.getInstance()
    return settings.showStatusBar && settings.showNavigationBarInBottom
  }
}