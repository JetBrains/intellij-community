// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.steps

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Point
import java.net.URL

open class ToolWindowLayoutsStep : NewUiOnboardingStep {
  protected open val ideHelpTopic: String? = "tool-windows.html"

  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val ideFrame = WindowManager.getInstance().getFrame(project) ?: return null
    val builder = GotItComponentBuilder(NewUiOnboardingBundle.message("tool.window.layouts.step.text"))
      .withHeader(NewUiOnboardingBundle.message("tool.window.layouts.step.header"))

    val topic = ideHelpTopic
    if (topic != null) {
      val ideHelpLink = NewUiOnboardingUtil.getHelpLink(topic)
      builder.withBrowserLink(NewUiOnboardingBundle.message("gotIt.learn.more"), URL(ideHelpLink))
    }

    val lottiePageData = withContext(Dispatchers.IO) {
      NewUiOnboardingUtil.createLottieAnimationPage(LOTTIE_JSON_PATH, ToolWindowLayoutsStep::class.java.classLoader)
    }
    lottiePageData?.let { (html, size) ->
      builder.withBrowserPage(html, size, withBorder = true)
    }
    return NewUiOnboardingStepData(builder, RelativePoint(ideFrame.rootPane, Point(0, 0)), position = null) // show in the center
  }

  companion object {
    private const val LOTTIE_JSON_PATH = "newUiOnboarding/ToolWindowLayoutsAnimation.json"
  }
}