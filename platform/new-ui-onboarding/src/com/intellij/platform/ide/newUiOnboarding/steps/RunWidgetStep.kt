// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.execution.RunManager
import com.intellij.execution.ui.RedesignedRunConfigurationSelector
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.ui.GotItComponentBuilder
import com.intellij.util.ui.JBPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.net.URL

class RunWidgetStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val actionButton = NewUiOnboardingUtil.findUiComponent(project) { button: ActionButtonWithText ->
      button.action is RedesignedRunConfigurationSelector
    } ?: return null

    val runPopup = NewUiOnboardingUtil.showNonClosablePopup(actionButton, disposable) {
      val action = actionButton.action as RedesignedRunConfigurationSelector
      val context = DataManager.getInstance().getDataContext(actionButton)
      val event = AnActionEvent.createFromInputEvent(null, ActionPlaces.NEW_UI_ONBOARDING, actionButton.presentation, context)
      var popup: JBPopup? = null
      if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
        // wrap popup creation into SlowOperations.ACTION_PERFORM, otherwise there are a lot of exceptions
        ActionUtil.performDumbAwareWithCallbacks(action, event) {
          popup = action.createPopup(event)
          if (popup != null) {
            Toggleable.setSelected(actionButton.presentation, true)
          }
        }
      }
      popup
    } ?: return null

    yield()  // wait for run configurations popup to be shown (it is a coroutine's invokeLater alternative)

    val anyRunConfigAvailable = RunManager.getInstance(project).allConfigurationsList.isNotEmpty()
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

    val point = NewUiOnboardingUtil.convertPointToFrame(project, runPopup.content, JBPoint(-4, 27)) ?: return null
    return NewUiOnboardingStepData(builder, point, Balloon.Position.atLeft)
  }

  companion object {
    private const val LOTTIE_JSON_PATH = "newUiOnboarding/RunWidgetAnimation.json"
  }
}