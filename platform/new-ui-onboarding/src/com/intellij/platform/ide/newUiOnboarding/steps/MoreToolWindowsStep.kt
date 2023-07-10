// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.toolWindow.MoreSquareStripeButton
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.yield
import java.awt.Point
import java.awt.event.MouseEvent

class MoreToolWindowsStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project): NewUiOnboardingStepData? {
    val actionButton = NewUiOnboardingUtil.findUiComponent(project) { button: ActionButton ->
      button is MoreSquareStripeButton
    } ?: return null

    val moreAction = actionButton.action
    val event = MouseEvent(actionButton, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false)
    ActionUtil.invokeAction(moreAction, actionButton, ActionPlaces.NEW_UI_ONBOARDING, event, null)
    yield()  // wait for more tool windows popup to be shown

    val actionsList = NewUiOnboardingUtil.findUiComponent(project) { list: JBList<*> ->
      val model = list.model
      (0 until model.size).any {
        (model.getElementAt(it) as? AnActionHolder)?.action is ActivateToolWindowAction
      }
    } ?: return null

    val builder = GotItComponentBuilder(NewUiOnboardingBundle.message("more.tool.windows.step.text"))
      .withHeader(NewUiOnboardingBundle.message("more.tool.windows.step.header"))

    val listPoint = Point(actionsList.width + JBUI.scale(4), JBUI.scale(23))
    val point = NewUiOnboardingUtil.convertPointToFrame(project, actionsList, listPoint) ?: return null
    return NewUiOnboardingStepData(builder, point, Balloon.Position.atRight)
  }
}