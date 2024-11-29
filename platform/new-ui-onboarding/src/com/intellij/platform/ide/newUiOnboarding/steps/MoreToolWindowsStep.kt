// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.UiComponentsUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.toolWindow.ShowMoreToolWindowsAction
import com.intellij.ui.GotItComponentBuilder
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.yield
import java.awt.Point

internal class MoreToolWindowsStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val actionButton = UiComponentsUtil.findUiComponent(project) { button: ActionButton ->
      button.action is ShowMoreToolWindowsAction
    } ?: return null

    val moreAction = actionButton.action as ShowMoreToolWindowsAction
    val morePopup = NewUiOnboardingUtil.showNonClosablePopup(
      disposable,
      createPopup = { NewUiOnboardingUtil.createPopupFromActionButton(actionButton) { event -> moreAction.createPopup(event) } },
      showPopup = { moreAction.showPopup(it) }
    ) ?: return null

    yield()  // wait for more tool windows popup to be shown

    val builder = GotItComponentBuilder(NewUiOnboardingBundle.message("more.tool.windows.step.text"))
      .withHeader(NewUiOnboardingBundle.message("more.tool.windows.step.header"))

    val isOnTheLeft = moreAction.isOnTheLeft
    val xLocation = if (isOnTheLeft) morePopup.content.width else 0
    val xOffset = JBUI.scale(4) * (if (isOnTheLeft) 1 else -1)
    val popupPoint = Point(xLocation + xOffset, JBUI.scale(23))
    val point = NewUiOnboardingUtil.convertPointToFrame(project, morePopup.content, popupPoint) ?: return null
    val position = if (isOnTheLeft) Balloon.Position.atRight else Balloon.Position.atLeft
    return NewUiOnboardingStepData(builder, point, position)
  }
}