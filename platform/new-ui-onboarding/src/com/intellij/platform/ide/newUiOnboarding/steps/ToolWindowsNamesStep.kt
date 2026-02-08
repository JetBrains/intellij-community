// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.UiComponentsSearchUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.toolWindow.ShowMoreToolWindowsAction
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.Point

internal class ToolWindowsNamesStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val actionButton = UiComponentsSearchUtil.findUiComponent(project) { button: ActionButton ->
      button.action is ShowMoreToolWindowsAction
    } ?: return null

    val builder = GotItComponentBuilder(NewUiOnboardingBundle.message("tool.windows.names.step.text"))
      .withHeader(NewUiOnboardingBundle.message("tool.windows.names.step.header"))
      .withImage(NewUiOnboardingUtil.getImage(NewUiOnboardingUtil.SHOW_TOOL_WINDOW_NAMES_IMAGE_PATH))

    val relativePoint = RelativePoint(actionButton, Point(actionButton.width + JBUI.scale(10), actionButton.height / 2))
    return NewUiOnboardingStepData(builder, relativePoint, Balloon.Position.atRight)
  }
}