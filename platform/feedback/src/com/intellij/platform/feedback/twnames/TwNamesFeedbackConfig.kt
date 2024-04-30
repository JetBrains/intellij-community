// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.twnames

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.toolWindow.getLastToolWindowNameToggleEvent
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate
import java.time.LocalDateTime
import java.time.Month

internal class TwNamesFeedbackConfig : InIdeFeedbackSurveyConfig {
  override val surveyId: String = "tw_names_feedback"
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2024, Month.JUNE, 1)
  override val requireIdeEAP: Boolean = false

  override fun checkIdeIsSuitable(): Boolean {
    return PlatformUtils.isIntelliJ() || PlatformUtils.isPyCharm() || PlatformUtils.isWebStorm() ||
           PlatformUtils.isGoIde() || PlatformUtils.isRubyMine() || PlatformUtils.isCLion() ||
           PlatformUtils.isPhpStorm()
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    val eventData = getLastToolWindowNameToggleEvent()

    return eventData != null
           && (optionDisabledAfterTry(eventData) || optionEnabledLongEnough(eventData))
  }

  private fun optionEnabledLongEnough(eventData: Pair<LocalDateTime, Boolean>): Boolean =
    eventData.first.plusHours(24).isBefore(LocalDateTime.now())

  private fun optionDisabledAfterTry(eventData: Pair<LocalDateTime, Boolean>): Boolean =
    !eventData.second

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      TwNamesFeedbackMessagesBundle.message("tw.names.notification.request.title", ApplicationNamesInfo.getInstance().fullProductName),
      TwNamesFeedbackMessagesBundle.message("tw.names.notification.request.content")
    )
  }

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return TwNamesFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {
  }
}