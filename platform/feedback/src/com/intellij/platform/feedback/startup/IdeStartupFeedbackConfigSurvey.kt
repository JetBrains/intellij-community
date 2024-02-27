// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.startup

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.platform.feedback.startup.bundle.IdeStartupFeedbackMessagesBundle
import com.intellij.platform.feedback.startup.dialog.IdeStartupFeedbackDialog
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate

class IdeStartupFeedbackConfigSurvey : InIdeFeedbackSurveyConfig {
  override val surveyId: String = "startup_feedback"
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2024, 5, 1)
  override val requireIdeEAP: Boolean = false

  override fun checkIdeIsSuitable(): Boolean {
    return PlatformUtils.isIntelliJ() || PlatformUtils.isPyCharm() || PlatformUtils.isWebStorm() ||
           PlatformUtils.isGoIde() || PlatformUtils.isRubyMine() || PlatformUtils.isCLion() ||
           PlatformUtils.isPhpStorm()
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    return true
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      IdeStartupFeedbackMessagesBundle.message("ide.startup.notification.request.title", ApplicationNamesInfo.getInstance().fullProductName),
      IdeStartupFeedbackMessagesBundle.message("ide.startup.notification.request.content")
    )
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
  }

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return IdeStartupFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {
  }
}