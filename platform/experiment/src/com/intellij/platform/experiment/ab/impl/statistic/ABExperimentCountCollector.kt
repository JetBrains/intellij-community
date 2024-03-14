// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.statistic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.platform.experiment.ab.impl.experiment.getABExperimentInstance

object ABExperimentCountCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("experiment.ab", 2)

  private val AB_EXPERIMENT_OPTION_USED = GROUP.registerEvent(
    "option.used",
    EventFields.StringValidatedByCustomRule("id", ABExperimentOptionIdValidationRule::class.java),
    EventFields.Int("group"),
    EventFields.Int("bucket")
  )

  fun logABExperimentOptionUsed() {
    val service = getABExperimentInstance()
    val userExperimentOptionId = service.getUserExperimentOptionId().value
    val userGroupNumber = service.getUserGroupNumber() ?: return
    val userBucket = service.getUserBucket()
    AB_EXPERIMENT_OPTION_USED.log(userExperimentOptionId, userGroupNumber, userBucket)
  }

  override fun getGroup(): EventLogGroup = GROUP
}