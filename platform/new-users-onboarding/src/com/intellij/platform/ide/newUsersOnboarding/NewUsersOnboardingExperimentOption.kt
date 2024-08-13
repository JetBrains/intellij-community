// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOption
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOptionId
import com.intellij.platform.experiment.ab.impl.option.ABExperimentOptionGroupSize

internal class NewUsersOnboardingExperimentOption : ABExperimentOption {
  override val id: ABExperimentOptionId = ABExperimentOptionId("newUsersOnboarding")

  override fun getGroupSizeForIde(isPopularIde: Boolean): ABExperimentOptionGroupSize {
    return ABExperimentOptionGroupSize(128)
  }

  override fun checkIdeIsSuitable(): Boolean = true

  /**
   * Experiment should be available only in 2024.2.1
   */
  override fun checkIdeVersionIsSuitable(): Boolean {
    val appInfo = ApplicationInfo.getInstance()
    return appInfo.majorVersion == "2024" && appInfo.minorVersion == "2.1"
  }
}