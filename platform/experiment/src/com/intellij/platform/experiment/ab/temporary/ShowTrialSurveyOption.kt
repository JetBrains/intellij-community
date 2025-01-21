// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.temporary

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.platform.experiment.ab.impl.experiment.ABExperiment
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOption
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOptionId
import com.intellij.platform.experiment.ab.impl.option.ABExperimentOptionGroupSize
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShowTrialSurveyOption : ABExperimentOption {
  override val id: ABExperimentOptionId = ABExperimentOptionId("showTrialSurvey")

  override fun getGroupSizeForIde(isPopularIde: Boolean): ABExperimentOptionGroupSize {
    return ABExperimentOptionGroupSize(128)
  }

  override fun checkIdeIsSuitable(): Boolean = PlatformUtils.isIdeaUltimate()

  /**
   * Experiment should be available only in 2024.3.3
   */
  override fun checkIdeVersionIsSuitable(): Boolean {
    val appInfo = ApplicationInfo.getInstance()
    return appInfo.majorVersion == "2024" && appInfo.minorVersion == "3.3"
  }

  @Suppress("CompanionObjectInExtension")
  companion object {
    @JvmStatic
    val isTrialSurveyEnabled: Boolean get() = System.getProperty("test.ide.trial.survey", "false").toBoolean() ||
      ABExperiment.getABExperimentInstance().isExperimentOptionEnabled(ShowTrialSurveyOption::class.java)
  }
}
