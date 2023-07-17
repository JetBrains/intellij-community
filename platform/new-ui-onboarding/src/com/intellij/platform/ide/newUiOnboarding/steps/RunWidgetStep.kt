// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.execution.actions.EditRunConfigurationsAction
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.awt.event.MouseEvent
import java.net.URL

class RunWidgetStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project): NewUiOnboardingStepData? {
    val runConfigurationsSelectorAction = ActionManager.getInstance().getAction("RedesignedRunConfigurationSelector")
                                          ?: return null
    val actionButton = NewUiOnboardingUtil.findUiComponent(project) { button: ActionButtonWithText ->
      button.action == runConfigurationsSelectorAction
    } ?: return null

    val event = MouseEvent(actionButton, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false)
    ActionUtil.invokeAction(runConfigurationsSelectorAction, actionButton, ActionPlaces.NEW_UI_ONBOARDING, event, null)
    yield()  // wait for run configurations popup to be shown

    val actionsList = NewUiOnboardingUtil.findUiComponent(project) { list: JBList<*> ->
      val model = list.model
      (0 until model.size).any {
        (model.getElementAt(it) as? AnActionHolder)?.action is EditRunConfigurationsAction
      }
    } ?: return null

    val model = actionsList.model
    val anyRunConfigAvailable = (0 until model.size).any {
      (model.getElementAt(it) as? AnActionHolder)?.action is RunConfigurationsComboBoxAction.SelectConfigAction
    }

    val ideHelpUrl = URL("https://www.jetbrains.com/help/idea/run-debug-configuration.html")
    val builder = GotItComponentBuilder {
      val linkText = browserLink(NewUiOnboardingBundle.message("run.widget.step.link"), ideHelpUrl)
      if (anyRunConfigAvailable) {
        NewUiOnboardingBundle.message("run.widget.step.text.config.exist", linkText)
      }
      else NewUiOnboardingBundle.message("run.widget.step.text.no.config", linkText)
    }
    builder.withHeader(NewUiOnboardingBundle.message("run.widget.step.header"))

    val lottiePageData = withContext(Dispatchers.IO) {
      NewUiOnboardingUtil.createLottieAnimationPage(LOTTIE_JSON_PATH, RunWidgetStep::class.java.classLoader)
    }
    lottiePageData?.let { (html, size) ->
      builder.withBrowserPage(html, size, withBorder = true)
    }

    val point = NewUiOnboardingUtil.convertPointToFrame(project, actionsList, JBPoint(-4, 27)) ?: return null
    return NewUiOnboardingStepData(builder, point, Balloon.Position.atLeft)
  }

  companion object {
    private const val LOTTIE_JSON_PATH = "newUiOnboarding/RunWidgetAnimation.json"
  }
}