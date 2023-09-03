// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil.NEW_UI_ON_FIRST_STARTUP
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil.ONBOARDING_PROPOSED_VERSION
import com.intellij.ui.ExperimentalUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewUiOnboardingStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val propertiesComponent = PropertiesComponent.getInstance()
    if (!propertiesComponent.isValueSet(NEW_UI_ON_FIRST_STARTUP)) {
      // remember what UI was enabled on first startup: old or new.
      // set property as string, because otherwise 'false' value won't be stored.
      propertiesComponent.setValue(NEW_UI_ON_FIRST_STARTUP, ExperimentalUI.isNewUI().toString())
    }

    if (NewUiOnboardingUtil.isOnboardingEnabled
        && ExperimentalUI.isNewUI()
        && propertiesComponent.getBoolean(ExperimentalUI.NEW_UI_SWITCH)
        && !propertiesComponent.getBoolean(NEW_UI_ON_FIRST_STARTUP)
        && !propertiesComponent.isValueSet(ONBOARDING_PROPOSED_VERSION)) {
      propertiesComponent.unsetValue(ExperimentalUI.NEW_UI_SWITCH)
      val version = ApplicationInfo.getInstance().build.asStringWithoutProductCodeAndSnapshot()
      propertiesComponent.setValue(ONBOARDING_PROPOSED_VERSION, version)

      project.putUserData(TipAndTrickManager.DISABLE_TIPS_FOR_PROJECT, true)
      withContext(Dispatchers.EDT) {
        NewUiOnboardingService.getInstance(project).showOnboardingDialog()
      }
    }
  }
}