// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.ExperimentalUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewUiOnboardingStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (NewUiOnboardingUtil.isOnboardingEnabled
        && PropertiesComponent.getInstance().getBoolean(ExperimentalUI.NEW_UI_FIRST_SWITCH)) {
      PropertiesComponent.getInstance().unsetValue(ExperimentalUI.NEW_UI_FIRST_SWITCH)
      withContext(Dispatchers.EDT) {
        NewUiOnboardingService.getInstance(project).showOnboardingDialog()
      }
    }
  }
}