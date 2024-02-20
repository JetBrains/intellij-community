// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
abstract class ABExperiment<T : ABExperimentConfigBase> {

  abstract val id: String

  protected abstract val experimentConfig: T

  protected abstract val testRegistryKey: String

  fun isExperimentEnabled(): Boolean {
    return getUserGroupKind() == GroupKind.Experimental
  }

  fun getUserGroupNumber(): Int {
    val testRegistryExperimentGroup = Registry.intValue(testRegistryKey, -1, -1, experimentConfig.totalNumberOfBuckets - 1)
    if (testRegistryExperimentGroup >= 0) return testRegistryExperimentGroup

    val bucket = getBucket()
    val experimentGroup = bucket % getTotalNumberOfGroups()
    return experimentGroup
  }

  fun getUserGroupKind(): GroupKind {
    val groupNumber = getUserGroupNumber()
    return if (isPopularIDE()) {
      when (groupNumber) {
        in experimentConfig.experimentalGroupNumbersForPopularIde -> GroupKind.Experimental
        in experimentConfig.controlGroupNumbersForPopularIde -> GroupKind.Control
        else -> GroupKind.Experimental
      }
    }
    else {
      when (groupNumber) {
        in experimentConfig.experimentalGroupNumbersForRegularIde -> GroupKind.Experimental
        in experimentConfig.controlGroupNumbersForRegularIde -> GroupKind.Control
        else -> GroupKind.Experimental
      }
    }
  }

  private fun getTotalNumberOfGroups(): Int {
    return if (isPopularIDE()) {
      experimentConfig.experimentalGroupNumbersForPopularIde.size + experimentConfig.controlGroupNumbersForPopularIde.size
    }
    else {
      experimentConfig.experimentalGroupNumbersForRegularIde.size + experimentConfig.controlGroupNumbersForRegularIde.size
    }
  }

  private fun getBucket(): Int {
    val eventLogConfiguration = EventLogConfiguration.getInstance()
    return eventLogConfiguration.bucket % experimentConfig.totalNumberOfBuckets
  }

  private fun isPopularIDE() = PlatformUtils.isIdeaUltimate() || PlatformUtils.isPyCharmPro()


  enum class GroupKind {
    Experimental,
    Control
  }
}