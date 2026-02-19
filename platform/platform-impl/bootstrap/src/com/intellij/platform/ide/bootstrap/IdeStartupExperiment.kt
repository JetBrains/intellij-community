// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.util.MathUtil
import com.intellij.util.PlatformUtils

object IdeStartupExperiment {

  private val LOG: Logger = logger<IdeStartupWizard>()

  enum class GroupKind {
    ExperimentalWizard,
    ExperimentalFeedbackSurvey,
    Control,
    Undefined
  }

  @Suppress("DEPRECATION")
  private val numberOfGroups = when {
    PlatformUtils.isIdeaUltimate() || PlatformUtils.isPyCharmPro() -> 10
    else -> 3
  }

  @Suppress("DEPRECATION")
  private fun getGroupKind(group: Int) = when {
    PlatformUtils.isIdeaUltimate() -> when (group) {
      in 0..5 -> GroupKind.ExperimentalWizard
      6, 7 -> GroupKind.ExperimentalFeedbackSurvey
      8, 9 -> GroupKind.Control
      else -> GroupKind.Undefined
    }
    PlatformUtils.isPyCharmPro() -> when (group) {
      in 0..7 -> GroupKind.ExperimentalWizard
      8, 9 -> GroupKind.Control
      else -> GroupKind.Undefined
    }
    else -> when (group) {
      0, 1 -> GroupKind.ExperimentalWizard
      2 -> GroupKind.Control
      else -> GroupKind.Undefined
    }
  }

  private fun asBucket(s: String) = MathUtil.nonNegativeAbs(s.hashCode()) % 256

  private fun getBucket(): Int {
    val deviceId = LOG.runAndLogException {
      DeviceIdManager.getOrGenerateId(object : DeviceIdManager.DeviceIdToken {}, "FUS")
    } ?: return 0
    return asBucket(deviceId)
  }

  val experimentGroup by lazy {
    val registryExperimentGroup = (System.getProperty("ide.transfer.wizard.experiment.group", "-1").toIntOrNull() ?: -1)
      .coerceIn(-1, numberOfGroups - 1)
    if (registryExperimentGroup >= 0) return@lazy registryExperimentGroup

    val bucket = getBucket()
    val experimentGroup = bucket % numberOfGroups
    experimentGroup
  }

  val experimentGroupKind: GroupKind by lazy {
    getGroupKind(experimentGroup)
  }

  internal fun isWizardExperimentEnabled(): Boolean {
    @Suppress("DEPRECATION")
    if (PlatformUtils.isCLion()) return false
    return when (experimentGroupKind) {
      GroupKind.ExperimentalWizard, GroupKind.Undefined -> true
      GroupKind.ExperimentalFeedbackSurvey, GroupKind.Control -> false
    }
  }

  fun isFeedbackSurveyExperimentEnabled(): Boolean {
    @Suppress("DEPRECATION")
    if (PlatformUtils.isCLion()) return false
    return when (experimentGroupKind) {
      GroupKind.ExperimentalWizard, GroupKind.Control -> false
      GroupKind.ExperimentalFeedbackSurvey, GroupKind.Undefined -> true
    }
  }
}