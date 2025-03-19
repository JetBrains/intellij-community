// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBundle
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUsersOnboarding.NewUsersOnboardingStatistics.OnboardingStopReason
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
  private val finishListener: () -> Unit = {},
) {
  private val disposable = Disposer.newDisposable()
  private val tourStartMillis = System.currentTimeMillis()

  private val initiallyVisibleToolWindowIds: List<String> = ToolWindowManagerEx.getInstanceEx(project)
    .toolWindows
    .mapNotNull { if (it.isVisible) it.id else null }

  private var curStepId: String? = null
  private var curStepStartMillis: Long? = null

  init {
    Disposer.register(parentDisposable, disposable)
    Disposer.register(disposable) {
      coroutineScope.cancel()
      // Restore initially visible tool windows on the tour end
      restoreVisibleToolWindows(initiallyVisibleToolWindowIds)
    }

    // log if user aborted the onboarding by closing the project
    project.messageBus.connect(disposable).subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectClosing(project: Project) {
        val stepId = curStepId ?: return
        val stepStartMillis = curStepStartMillis ?: return
        NewUsersOnboardingStatistics.logOnboardingStopped(project, stepId, OnboardingStopReason.PROJECT_CLOSED, tourStartMillis, stepStartMillis)
      }
    })
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

    val stepStartMillis = System.currentTimeMillis()
    curStepId = stepId
    curStepStartMillis = stepStartMillis
    NewUsersOnboardingStatistics.logStepStarted(project, stepId)

    val gotItData = step.performStep(project, stepDisposable)
    if (gotItData == null) {
      runStep(ind + 1)
      return
    }

    val showInCenter = gotItData.position == null
    val builder = gotItData.builder
    builder.withStepNumber("${ind + 1}/${steps.size}")
      .onEscapePressed {
        finishOnboarding(OnboardingStopReason.ESCAPE_PRESSED)
      }
      .onLinkClick { NewUsersOnboardingStatistics.logLinkClicked(project, stepId) }
      .requestFocus(true)

    if (ind < steps.lastIndex) {
      builder.withButtonLabel(NewUiOnboardingBundle.message("gotIt.button.next"))
        .onButtonClick {
          Disposer.dispose(stepDisposable)
          NewUsersOnboardingStatistics.logStepFinished(project, stepId, stepStartMillis)
          coroutineScope.launch(Dispatchers.EDT) {
            runStep(ind + 1)
          }
        }
        .withSecondaryButton(NewUiOnboardingBundle.message("gotIt.button.skipAll")) {
          finishOnboarding(OnboardingStopReason.SKIP_ALL)
        }
    }
    else {
      builder.withButtonLabel(NewUsersOnboardingBundle.message("gotIt.button.finishTour"))
        .withContrastButton(true)
        .onButtonClick {
          NewUsersOnboardingStatistics.logStepFinished(project, stepId, stepStartMillis)
          finishOnboarding(reason = null)
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

  /**
   * @param reason used for statistics reporting.
   * If reason is null, then we consider that onboarding is fully completed and report it correspondingly.
   */
  fun finishOnboarding(reason: OnboardingStopReason?) {
    Disposer.dispose(disposable)
    finishListener()

    if (reason != null) {
      val stepId = curStepId
      val stepStartMillis = curStepStartMillis
      if (stepId != null && stepStartMillis != null) {
        NewUsersOnboardingStatistics.logOnboardingStopped(project, stepId, reason, tourStartMillis, stepStartMillis)
      }
      else error("finishOnboarding called before the first step was started")
    }
    else {
      NewUsersOnboardingStatistics.logOnboardingFinished(project, tourStartMillis)
    }
  }

  private fun restoreVisibleToolWindows(visibleToolWindowIds: List<String>) {
    val toolWindowManager = ToolWindowManagerEx.getInstanceEx(project)
    for (toolWindow in toolWindowManager.toolWindows) {
      if (!toolWindow.isVisible && toolWindow.id in visibleToolWindowIds) {
        toolWindow.show()
      }
      else if (toolWindow.isVisible && toolWindow.id !in visibleToolWindowIds) {
        toolWindow.hide()
      }
    }
  }
}