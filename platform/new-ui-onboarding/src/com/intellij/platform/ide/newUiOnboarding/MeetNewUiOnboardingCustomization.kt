// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.ide.ui.experimental.meetNewUi.MeetNewUiCustomization
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStatistics.OnboardingStartingPlace
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.gridLayout.UnscaledGaps

private class MeetNewUiOnboardingCustomization : MeetNewUiCustomization {
  override fun addButtons(project: Project, row: Row) {
    if (NewUiOnboardingUtil.isOnboardingEnabled) {
      @Suppress("DialogTitleCapitalization")
      row.link(NewUiOnboardingBundle.message("start.tour")) {
        NewUiOnboardingService.getInstance(project).startOnboarding()
        ToolWindowManagerEx.getInstanceEx(project).hideToolWindow(ToolWindowId.MEET_NEW_UI, true)
        NewUiOnboardingStatistics.logOnboardingStarted(project, OnboardingStartingPlace.CONFIGURE_NEW_UI_TOOLWINDOW)
      }.customize(UnscaledGaps(left = 16))
    }
  }

  override fun shouldCreateToolWindow(): Boolean {
    return NewUiOnboardingUtil.shouldProposeOnboarding()
  }

  override fun showToolWindowOnStartup(): Boolean {
    return !NewUiOnboardingUtil.isOnboardingEnabled
  }
}