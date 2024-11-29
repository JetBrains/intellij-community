// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.ide.actions.SearchEverywhereAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.UiComponentsUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Point
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal class SearchEverywhereStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val searchEverywhereButton = UiComponentsUtil.findUiComponent(project) { button: ActionButton ->
      button.action is SearchEverywhereAction
    } ?: return null

    val builder = GotItComponentBuilder {
      val shiftShortcut = shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0))
      NewUsersOnboardingBundle.message("search.everywhere.step.text", shiftShortcut)
    }
    builder.withHeader(NewUsersOnboardingBundle.message("search.everywhere.step.header"))

    val lottiePageData = withContext(Dispatchers.IO) {
      NewUiOnboardingUtil.createLottieAnimationPage(LOTTIE_JSON_PATH, SearchEverywhereStep::class.java.classLoader)
    }
    lottiePageData?.let { (html, size) ->
      builder.withBrowserPage(html, size, withBorder = true)
    }

    val point = Point(searchEverywhereButton.width / 2, searchEverywhereButton.height + JBUI.scale(3))
    return NewUiOnboardingStepData(builder, RelativePoint(searchEverywhereButton, point), Balloon.Position.below)
  }

  companion object {
    private const val LOTTIE_JSON_PATH = "animations/SearchEverywhereAnimation.json"
  }
}