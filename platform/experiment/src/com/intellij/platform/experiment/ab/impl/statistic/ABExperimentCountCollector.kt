// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.statistic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.platform.experiment.ab.impl.ABExperimentDecision
import com.intellij.platform.experiment.ab.impl.reportableName

internal object ABExperimentCountCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("experiment.ab", 4)

  private val AB_EXPERIMENT_OPTION_USED = GROUP.registerEvent(
    "option.used",
    EventFields.StringValidatedByCustomRule("id", ABExperimentOptionIdValidationRule::class.java),
    EventFields.Boolean("is_control_group"),
    EventFields.Int("bucket")
  )

  fun logABExperimentOptionUsed(decision: ABExperimentDecision) {
    val optionName = decision.option.reportableName()
    AB_EXPERIMENT_OPTION_USED.log(optionName, decision.isControlGroup, decision.bucketNumber)
  }

  override fun getGroup(): EventLogGroup = GROUP
}