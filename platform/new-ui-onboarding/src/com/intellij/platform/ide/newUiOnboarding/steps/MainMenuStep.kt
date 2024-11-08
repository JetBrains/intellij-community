// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.UiComponentsUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.MainMenuButton
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.Point

internal class MainMenuStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val button = UiComponentsUtil.findUiComponent(project) { button: ActionButton ->
      button.action is MainMenuButton.ShowMenuAction
    } ?: return null

    val builder = GotItComponentBuilder { NewUiOnboardingBundle.message("mainMenu.step.text", shortcut("MainMenuButton.ShowMenu")) }
    builder.withHeader(NewUiOnboardingBundle.message("mainMenu.step.header"))

    val relativePoint = RelativePoint(button, Point(button.width / 2, button.height + JBUI.scale(10)))
    return NewUiOnboardingStepData(builder, relativePoint, Balloon.Position.below)
  }

  override fun isAvailable(): Boolean {
    return !SystemInfoRt.isMac && !UISettings.getInstance().separateMainMenu
  }
}