// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.openapi.wm.impl.headertoolbar.ProjectToolbarWidgetAction
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil.findUiComponent
import com.intellij.ui.ClientProperty
import com.intellij.ui.GotItComponentBuilder
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.yield
import java.awt.Point

class ProjectWidgetStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val widget = findUiComponent(project) { widget: ToolbarComboWidget ->
      ClientProperty.get(widget, CustomComponentAction.ACTION_KEY) is ProjectToolbarWidgetAction
    } ?: return null

    val popup = NewUiOnboardingUtil.showToolbarWidgetPopup(widget, disposable) ?: return null

    yield()  // wait for popup to be shown

    val builder = GotItComponentBuilder(NewUiOnboardingBundle.message("project.widget.step.text"))
    builder.withHeader(NewUiOnboardingBundle.message("project.widget.step.header"))

    val popupPoint = Point(popup.content.width + JBUI.scale(4), JBUI.scale(32))
    val point = NewUiOnboardingUtil.convertPointToFrame(project, popup.content, popupPoint) ?: return null
    return NewUiOnboardingStepData(builder, point, Balloon.Position.atRight)
  }
}