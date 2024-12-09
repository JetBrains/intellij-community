// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.statistic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.platform.experiment.ab.impl.experiment.ABExperiment
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentImpl
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOptionId

object ABExperimentCountCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("experiment.ab", 3)

  /**
   * For the case when user enables plugin and then disables it.
   *
   * When the plugin is disabled, then a corresponding option is missing and the validation rule will reject such an option id,
   * because the option is not present at runtime.
   *
   * To overcome such cases, an original option id is replaced with an artificial one for statistics.
   */
  internal val OPTION_ID_MISSING = ABExperimentOptionId("missing.option")

  private val AB_EXPERIMENT_OPTION_USED = GROUP.registerEvent(
    "option.used",
    EventFields.StringValidatedByCustomRule("id", ABExperimentOptionIdValidationRule::class.java),
    EventFields.Int("group"),
    EventFields.Int("bucket")
  )

  fun logABExperimentOptionUsed(userOptionId: ABExperimentOptionId?, userGroupNumber: Int, userBucketNumber: Int) {
    if (userOptionId == null) {
      return
    }

    if (userOptionId == ABExperimentImpl.OPTION_ID_FREE_GROUP) {
      AB_EXPERIMENT_OPTION_USED.log(userOptionId.value, userGroupNumber, userBucketNumber)
      return
    }

    val option = ABExperimentImpl.getJbABExperimentOptionList().find { it.id.value == userOptionId.value }
    if (option != null) {
      AB_EXPERIMENT_OPTION_USED.log(option.id.value, userGroupNumber, userBucketNumber)
      return
    }

    AB_EXPERIMENT_OPTION_USED.log(OPTION_ID_MISSING.value, userGroupNumber, userBucketNumber)
  }

  override fun getGroup(): EventLogGroup = GROUP
}