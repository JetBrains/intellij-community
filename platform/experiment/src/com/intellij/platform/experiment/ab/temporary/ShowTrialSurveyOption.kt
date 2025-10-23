// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.temporary

import com.intellij.platform.experiment.ab.impl.ABExperimentOption
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShowTrialSurveyOption {
  @Suppress("CompanionObjectInExtension")
  companion object {
    @JvmStatic
    val isTrialSurveyEnabled: Boolean get() = System.getProperty("test.ide.trial.survey", "false").toBoolean() ||
                                              ABExperimentOption.SHOW_TRIAL_SURVEY.isEnabled()
  }
}
