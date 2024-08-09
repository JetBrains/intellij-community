// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class NewUsersOnboardingStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode || AppMode.isRemoteDevHost()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val newUsersOnboardingService = project.serviceAsync<NewUsersOnboardingService>()
    if (newUsersOnboardingService.shouldShowOnboardingDialog()) {
      // Show dialog a little bit later, because IDE Frame appeared quite recently.
      delay(1500)

      withContext(Dispatchers.EDT) {
        // Check for the second time to exclude showing dialog more than once if multiple projects are opening simultaneously.
        if (newUsersOnboardingService.shouldShowOnboardingDialog()) {
          newUsersOnboardingService.showOnboardingDialog()
        }
      }
    }
  }
}