// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.execution.RunManager
import com.intellij.execution.ui.RedesignedRunConfigurationSelector
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.UiComponentsUtil
import com.intellij.openapi.ui.popup.Balloon
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
import org.jetbrains.annotations.ApiStatus
import java.net.URL

@ApiStatus.Internal
open class RunWidgetStep : NewUiOnboardingStep {
  private val ideHelpTopic = "run-debug-configuration.html"

  protected open val animationPath: String = "newUiOnboarding/RunWidgetAnimation.json"
  protected open val animationClassLoader: ClassLoader
    get() = RunWidgetStep::class.java.classLoader

  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val actionButton = UiComponentsUtil.findUiComponent(project) { button: ActionButtonWithText ->
      button.action is RedesignedRunConfigurationSelector
    } ?: return null

    val action = actionButton.action as RedesignedRunConfigurationSelector
    val runPopup = NewUiOnboardingUtil.showNonClosablePopup(
      disposable,
      createPopup = { NewUiOnboardingUtil.createPopupFromActionButton(actionButton) { event -> action.createPopup(event) } },
      showPopup = { popup -> popup.showUnderneathOf(actionButton) }
    ) ?: return null

    yield()  // wait for run configurations popup to be shown (it is a coroutine's invokeLater alternative)

    val anyRunConfigAvailable = RunManager.getInstance(project).allConfigurationsList.isNotEmpty()
    val ideHelpLink = NewUiOnboardingUtil.getHelpLink(ideHelpTopic)
    val builder = GotItComponentBuilder {
      val linkText = browserLink(NewUiOnboardingBundle.message("run.widget.step.link"), URL(ideHelpLink))
      if (anyRunConfigAvailable) {
        NewUiOnboardingBundle.message("run.widget.step.text.config.exist", linkText)
      }
      else NewUiOnboardingBundle.message("run.widget.step.text.no.config", linkText)
    }
    builder.withHeader(NewUiOnboardingBundle.message("run.widget.step.header"))

    val lottiePageData = withContext(Dispatchers.IO) {
      NewUiOnboardingUtil.createLottieAnimationPage(animationPath, animationClassLoader)
    }
    lottiePageData?.let { (html, size) ->
      builder.withBrowserPage(html, size, withBorder = true)
    }

    val point = NewUiOnboardingUtil.convertPointToFrame(project, runPopup.content, JBPoint(-4, 27)) ?: return null
    return NewUiOnboardingStepData(builder, point, Balloon.Position.atLeft)
  }
}
