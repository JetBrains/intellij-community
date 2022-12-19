// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.productivityMetric.statistics

import com.intellij.feedback.productivityMetric.bundle.ProductivityFeedbackBundle
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class ProductivityMetricCountCollector : CounterUsagesCollector() {
  companion object {
    private val ALLOWED_VALUES = listOf<String>(
      ProductivityFeedbackBundle.message("dialog.combobox.item.1"),
      ProductivityFeedbackBundle.message("dialog.combobox.item.2"),
      ProductivityFeedbackBundle.message("dialog.combobox.item.3"),
      ProductivityFeedbackBundle.message("dialog.combobox.item.4"),
      ProductivityFeedbackBundle.message("dialog.combobox.item.5"),
      ProductivityFeedbackBundle.message("dialog.combobox.item.6"),
      ProductivityFeedbackBundle.message("dialog.combobox.item.7"),
      ProductivityFeedbackBundle.message("dialog.combobox.item.8"),
      "No data"
    )

    private val GROUP = EventLogGroup("feedback.productivity.metric", 1)

    private val FEEDBACK = GROUP.registerEvent(
      "feedback", EventFields.Int("productivity"),
      EventFields.Int("proficiency"),
      EventFields.String("experience", ALLOWED_VALUES)
    )

    fun logProductivityMetricFeedback(productivity: Int, proficiency: Int, experience: String) {
      FEEDBACK.log(productivity, proficiency, experience)
    }
  }

  override fun getGroup(): EventLogGroup = GROUP
}