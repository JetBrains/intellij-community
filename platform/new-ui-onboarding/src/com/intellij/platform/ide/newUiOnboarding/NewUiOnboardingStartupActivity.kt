// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil.NEW_UI_ON_FIRST_STARTUP
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil.ONBOARDING_PROPOSED_VERSION
import com.intellij.ui.ExperimentalUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class NewUiOnboardingStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val propertyManager = serviceAsync<PropertiesComponent>()
    if (!propertyManager.isValueSet(NEW_UI_ON_FIRST_STARTUP)) {
      // remember what UI was enabled on first startup: old or new.
      // set property as string, because otherwise 'false' value won't be stored.
      propertyManager.setValue(NEW_UI_ON_FIRST_STARTUP, ExperimentalUI.isNewUI().toString())
    }

    if (NewUiOnboardingUtil.shouldProposeOnboarding()) {
      propertyManager.unsetValue(ExperimentalUI.NEW_UI_SWITCH)
      val version = ApplicationInfo.getInstance().build.asStringWithoutProductCodeAndSnapshot()
      propertyManager.setValue(ONBOARDING_PROPOSED_VERSION, version)

      project.putUserData(TipAndTrickManager.DISABLE_TIPS_FOR_PROJECT, true)
      withContext(Dispatchers.EDT) {
        project.serviceAsync<NewUiOnboardingService>().showOnboardingDialog()
      }
    }
  }
}