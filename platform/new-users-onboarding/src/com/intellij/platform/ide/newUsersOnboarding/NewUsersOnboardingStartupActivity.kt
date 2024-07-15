// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.ide.newUsersOnboarding.NewUsersOnboardingService.Companion.NEW_USERS_ONBOARDING_DIALOG_SHOWN_PROPERTY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NewUsersOnboardingStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val propertiesComponent = serviceAsync<PropertiesComponent>()
    if (serviceAsync<NewUsersOnboardingExperiment>().isEnabled() &&
        !propertiesComponent.getBoolean(NEW_USERS_ONBOARDING_DIALOG_SHOWN_PROPERTY) &&
        ConfigImportHelper.isNewUser()) {
      propertiesComponent.setValue(NEW_USERS_ONBOARDING_DIALOG_SHOWN_PROPERTY, true)
      withContext(Dispatchers.EDT) {
        project.serviceAsync<NewUsersOnboardingService>().showOnboardingDialog()
      }
    }
  }
}