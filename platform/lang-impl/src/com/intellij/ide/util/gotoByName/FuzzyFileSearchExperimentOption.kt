// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.platform.experiment.ab.impl.experiment.ABExperiment
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOption
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOptionId
import com.intellij.platform.experiment.ab.impl.option.ABExperimentOptionGroupSize
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal fun isFuzzyFileSearchEnabled(): Boolean {
  return ABExperiment.getABExperimentInstance().isExperimentOptionEnabled(FuzzyFileSearchExperimentOption::class.java)
}

@ApiStatus.Internal
internal class FuzzyFileSearchExperimentOption : ABExperimentOption {
  override val id: ABExperimentOptionId = ABExperimentOptionId("fuzzyFileSearch")

  override fun getGroupSizeForIde(isPopularIde: Boolean): ABExperimentOptionGroupSize {
    return ABExperimentOptionGroupSize(128)
  }

  override fun checkIdeIsSuitable(): Boolean = true

  override fun checkIdeVersionIsSuitable(): Boolean {
    val appInfo = ApplicationInfo.getInstance()
    return appInfo.isEAP && appInfo.majorVersion == "2025" && appInfo.minorVersionMainPart == "2"
  }
}