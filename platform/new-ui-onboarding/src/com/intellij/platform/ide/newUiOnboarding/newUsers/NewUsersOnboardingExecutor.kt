// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.newUsers

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.swing.JComponent

internal class NewUsersOnboardingExecutor(
  private val project: Project,
  private val steps: List<Pair<String, NewUiOnboardingStep>>,
  private val coroutineScope: CoroutineScope,
  parentDisposable: Disposable,
) {
  private val disposable = Disposer.newDisposable()

  private var curStepId: String? = null

  init {
    Disposer.register(parentDisposable, disposable)
    Disposer.register(disposable) {
      coroutineScope.cancel()
    }
  }

  fun start() {
    coroutineScope.launch(Dispatchers.EDT) {
      runStep(0)
    }
  }

  private suspend fun runStep(ind: Int) {
    if (ind >= steps.size) {
      return
    }

    val (stepId, step) = steps[ind]
    val stepDisposable = Disposer.newCheckedDisposable()
    Disposer.register(disposable, stepDisposable)

    curStepId = stepId

    val gotItData = step.performStep(project, stepDisposable)
    if (gotItData == null) {
      runStep(ind + 1)
      return
    }

    val showInCenter = gotItData.position == null
    val builder = gotItData.builder
    builder.withStepNumber("${ind + 1}/${steps.size}")
      .onEscapePressed {
        finishOnboarding()
      }
      .requestFocus(true)

    if (ind < steps.lastIndex) {
      builder.withButtonLabel(NewUiOnboardingBundle.message("gotIt.button.next"))
        .onButtonClick {
          Disposer.dispose(stepDisposable)
          coroutineScope.launch(Dispatchers.EDT) {
            runStep(ind + 1)
          }
        }
        .withSecondaryButton(NewUiOnboardingBundle.message("gotIt.button.skipAll")) {
          finishOnboarding()
        }
    }
    else {
      builder.withButtonLabel(NewUiOnboardingBundle.message("gotIt.button.finishTour"))
        .withContrastButton(true)
        .onButtonClick {
          finishOnboarding()
        }
    }

    val balloon = builder.build(stepDisposable) {
      // do not show the pointer if the balloon should be centered
      setShowCallout(!showInCenter)
    }

    if (showInCenter) {
      balloon.showInCenterOf(gotItData.relativePoint.originalComponent as JComponent)
    }
    else {
      balloon.show(gotItData.relativePoint, gotItData.position)
    }
  }

  private fun finishOnboarding() {
    Disposer.dispose(disposable)
  }
}