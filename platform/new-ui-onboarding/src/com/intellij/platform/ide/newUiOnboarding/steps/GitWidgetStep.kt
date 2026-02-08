// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.UiComponentsSearchUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.ui.ClientProperty
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Point

internal class GitWidgetStep : NewUiOnboardingStep {

  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val button = UiComponentsSearchUtil.findUiComponent(project) { button: ToolbarComboButton ->
      val action = ClientProperty.get(button, CustomComponentAction.ACTION_KEY)
      action?.templateText == "VCS Widget"
    } ?: return null

    val action = ClientProperty.get(button, CustomComponentAction.ACTION_KEY) as? ExpandableComboAction ?: return null
    val popup = NewUiOnboardingUtil.showToolbarComboButtonPopup(button, action, disposable) ?: return null

    val text = if (repositoryExists(popup)) NewUiOnboardingBundle.message("newUiOnboarding.git.widget.step.text.with.repo")
    else NewUiOnboardingBundle.message("newUiOnboarding.git.widget.step.text.no.repo")
    val builder = GotItComponentBuilder(text)
      .withHeader(NewUiOnboardingBundle.message("newUiOnboarding.git.widget.step.header"))

    val atLeft = NewUiOnboardingUtil.isPopupLeftSide(popup)
    val x = if (atLeft) -JBUI.scale(4) else popup.content.width + JBUI.scale(4)
    val popupPoint = Point(x, JBUI.scale(32))
    val point = NewUiOnboardingUtil.convertPointToFrame(project, popup.content, popupPoint) ?: return null
    return NewUiOnboardingStepData(builder, point, if (atLeft) Balloon.Position.atLeft else Balloon.Position.atRight)
  }

  private suspend fun repositoryExists(popup: JBPopup): Boolean {
    val actionsList = UIUtil.findComponentOfType(popup.content, JBList::class.java) ?: return true
    return NewUiOnboardingUtil.findActionItem(actionsList, "Git.Init") == null
  }
}
