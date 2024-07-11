// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.newUsers

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class NewUsersOnboardingService(private val project: Project, private val coroutineScope: CoroutineScope) {
  fun showOnboardingDialog() {
    val dialog = NewUsersOnboardingDialog(project)
    val startTour = dialog.showAndGet()
    if (startTour) {
      startOnboarding()
    }
    else {
      // TODO: show notification
    }
  }

  fun startOnboarding() {
    // TODO: start onboarding
  }

  companion object {
    fun getInstance(project: Project): NewUsersOnboardingService = project.service<NewUsersOnboardingService>()
  }
}