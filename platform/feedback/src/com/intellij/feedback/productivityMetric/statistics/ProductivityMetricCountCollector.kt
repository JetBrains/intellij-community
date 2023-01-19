// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.productivityMetric.statistics

import com.intellij.feedback.productivityMetric.bundle.ProductivityFeedbackBundle
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class ProductivityMetricCountCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("feedback.productivity.metric", 2)

    private val FEEDBACK = GROUP.registerEvent(
      "feedback", EventFields.Int("productivity"),
      EventFields.Int("proficiency"), EventFields.Int("experience")
    )

    fun logProductivityMetricFeedback(productivity: Int, proficiency: Int, experience: Int) {
      FEEDBACK.log(productivity, proficiency, experience)
    }
  }

  override fun getGroup(): EventLogGroup = GROUP
}