// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.option

import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentImpl
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOption
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOptionId
import com.intellij.platform.experiment.ab.impl.experiment.getABExperimentInstance


fun isControlOptionEnabled(): Boolean {
  val abImpl = getABExperimentInstance() as? ABExperimentImpl ?: return false
  return abImpl.getUserExperimentOption() is ABExperimentControlOption
}

internal class ABExperimentControlOption : ABExperimentOption {

  override val id: ABExperimentOptionId = ABExperimentOptionId("control.option")

  override fun getGroupSizeForIde(isPopularIde: Boolean): ABExperimentOptionGroupSize {
    return ABExperimentOptionGroupSize(32)
  }

  override fun checkIdeIsSuitable(): Boolean {
    return true
  }

  override fun checkIdeVersionIsSuitable(): Boolean {
    return true
  }
}