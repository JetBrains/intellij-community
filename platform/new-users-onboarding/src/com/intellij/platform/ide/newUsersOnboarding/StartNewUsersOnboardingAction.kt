// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.newUsersOnboarding.NewUsersOnboardingStatistics.OnboardingStartingPlace

private class StartNewUsersOnboardingAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    NewUsersOnboardingService.getInstance(project).startOnboarding()
    NewUsersOnboardingStatistics.logOnboardingStarted(project, OnboardingStartingPlace.ACTION)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && service<NewUsersOnboardingExperiment>().isEnabled()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}