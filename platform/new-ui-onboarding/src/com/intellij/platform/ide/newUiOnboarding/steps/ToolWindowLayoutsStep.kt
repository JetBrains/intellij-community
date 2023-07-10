// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import java.net.URL

class ToolWindowLayoutsStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project): NewUiOnboardingStepData? {
    val ideFrame = WindowManager.getInstance().getFrame(project) ?: return null
    val builder = GotItComponentBuilder(NewUiOnboardingBundle.message("tool.window.layouts.step.text"))
      .withHeader(NewUiOnboardingBundle.message("tool.window.layouts.step.header"))
      .withBrowserLink(NewUiOnboardingBundle.message("gotIt.learn.more"),
                       URL("https://www.jetbrains.com/help/idea/tool-windows.html"))
    return NewUiOnboardingStepData(builder, RelativePoint(ideFrame.rootPane, Point(0, 0)), position = null) // show in the center
  }
}