// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.feedback

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate
import java.time.Month

class KotlinDebuggerFeedbackSurveyConfig : InIdeFeedbackSurveyConfig {

  override val surveyId: String = "kotlin_debugger_feedback_survey"
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2024, Month.JULY, 1)
  override val requireIdeEAP: Boolean = false

  private val minimalNumberOfDebuggerUsage = 5

  override fun checkIdeIsSuitable(): Boolean {
    return PlatformUtils.isIdeaCommunity() || PlatformUtils.isIdeaUltimate()
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    return ApplicationInfo.getInstance().fullVersion.startsWith("2024.1") &&
           UsageTracker.kotlinDebuggedTimes() >= minimalNumberOfDebuggerUsage
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.notification.request.title"),
      ""
    )
  }

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return KotlinDebuggerSurveyFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {
  }
}