// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.newUiOnboarding.steps

import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.ide.newUiOnboarding.NewUiOnboardingUtil.findUiComponent
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.openapi.wm.impl.headertoolbar.ProjectToolbarWidgetAction
import com.intellij.ui.ClientProperty
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.yield
import java.awt.Point

class ProjectWidgetStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project): NewUiOnboardingStepData? {
    val widget = findUiComponent(project) { widget: ToolbarComboWidget ->
      ClientProperty.get(widget, CustomComponentAction.ACTION_KEY) is ProjectToolbarWidgetAction
    } ?: return null

    widget.doExpand(e = null)
    yield()  // wait for popup to be shown

    val actionsList = findUiComponent(project) { list: JBList<*> ->
      val model = list.model
      (0 until model.size).any {
        (model.getElementAt(it) as? AnActionHolder)?.action is ReopenProjectAction
      }
    } ?: return null

    val builder = GotItComponentBuilder(NewUiOnboardingBundle.message("project.widget.step.text"))
    builder.withHeader(NewUiOnboardingBundle.message("project.widget.step.header"))

    val listPoint = Point(actionsList.width + JBUI.scale(4), JBUI.scale(32))
    val point = NewUiOnboardingUtil.convertPointToFrame(project, actionsList, listPoint) ?: return null
    return NewUiOnboardingStepData(builder, point, Balloon.Position.atRight)
  }
}