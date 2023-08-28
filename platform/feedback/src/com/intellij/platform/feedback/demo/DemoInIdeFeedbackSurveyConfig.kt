// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.demo

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.demo.bundle.DemoFeedbackBundle
import com.intellij.platform.feedback.demo.dialog.DemoFeedbackDialog
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate

class DemoInIdeFeedbackSurveyConfig : InIdeFeedbackSurveyConfig {

  override val surveyId: String = "in_ide_demo_survey"
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(1999, 6, 11)
  override val requireIdeEAP: Boolean = true

  private val suitableIdeVersion = "2023.2"
  override fun checkIdeIsSuitable(): Boolean {
    return !PlatformUtils.isRider()
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    return suitableIdeVersion == ApplicationInfoEx.getInstanceEx().shortVersion &&
           false
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {
    // do nothing
  }

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return DemoFeedbackDialog(project, forTest)
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      DemoFeedbackBundle.message("notification.request.title"),
      DemoFeedbackBundle.message("notification.request.content")
    )
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    // do nothing
  }
}