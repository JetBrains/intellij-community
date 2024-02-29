// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.startup

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.platform.feedback.startup.dialog.IdeStartupFeedbackDialog.Companion.COLLECTOR_THIRD_QUESTION_OPTIONS
import com.intellij.platform.feedback.startup.dialog.IdeStartupFeedbackDialog.Companion.FIRST_QUESTION_OPTIONS
import com.intellij.platform.feedback.startup.dialog.IdeStartupFeedbackDialog.Companion.FORTH_QUESTION_OPTIONS_PLUS_NONE
import com.intellij.platform.feedback.startup.dialog.IdeStartupFeedbackDialog.Companion.SECOND_QUESTION_OPTIONS

internal object IdeStartupFeedbackCountCollector : CounterUsagesCollector() {
  internal val GROUP = EventLogGroup("feedback.in.ide.startup.feedback", 1)

  private val FEEDBACK_ANSWER_FIRST_QUESTION = GROUP.registerEvent("first.question",
                                                                   EventFields.String("answer", FIRST_QUESTION_OPTIONS))

  private val FEEDBACK_ANSWER_SECOND_QUESTION = GROUP.registerEvent("second.question",
                                                                    EventFields.String("answer", SECOND_QUESTION_OPTIONS))

  private val FEEDBACK_ANSWER_THIRD_QUESTION = GROUP.registerEvent("third.question",
                                                                   EventFields.String("answer", COLLECTOR_THIRD_QUESTION_OPTIONS))

  private val FEEDBACK_ANSWER_FORTH_QUESTION = GROUP.registerEvent("forth.question",
                                                                   EventFields.StringList("answer", FORTH_QUESTION_OPTIONS_PLUS_NONE))

  internal fun logFeedbackFirstQuestionAnswer(value: String) {
    FEEDBACK_ANSWER_FIRST_QUESTION.log(value)
  }

  internal fun logFeedbackSecondQuestionAnswer(value: String) {
    FEEDBACK_ANSWER_SECOND_QUESTION.log(value)
  }

  internal fun logFeedbackThirdQuestionAnswer(value: String) {
    FEEDBACK_ANSWER_THIRD_QUESTION.log(value)
  }

  internal fun logFeedbackForthQuestionAnswer(values: List<String>) {
    FEEDBACK_ANSWER_FORTH_QUESTION.log(values)
  }

  override fun getGroup(): EventLogGroup = GROUP
}