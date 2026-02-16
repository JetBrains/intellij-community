// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.editor.smoothcaret

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.ActionBasedFeedbackConfig
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.FeedbackSurveyType
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.InIdeFeedbackSurveyType
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

class SmoothCaretFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> = InIdeFeedbackSurveyType(SmoothCaretSurveyConfig)
}

object SmoothCaretSurveyConfig : InIdeFeedbackSurveyConfig, ActionBasedFeedbackConfig {
  
  override val surveyId: String
    get() = "smooth_caret"

  // Survey only during EAP period
  // EAP release: February 10, 2026
  // Actual IDE release: March 17, 2026
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2026, 3, 17)

  override val requireIdeEAP: Boolean = true

  // Number of days to wait after first EAP run before showing survey
  private const val DAYS_AFTER_FIRST_RUN = 2

  override fun checkIdeIsSuitable(): Boolean = PlatformUtils.isJetBrainsProduct()

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return SmoothCaretFeedbackDialog(project, forTest)
  }

  // Always true because separate conditions are used for actions and notifications
  override fun checkExtraConditionSatisfied(project: Project): Boolean = true

  override fun checkExtraConditionSatisfiedForNotification(project: Project): Boolean {
    val usageStorage = SmoothCaretUsageLocalStorage.getInstance()
    val editorSettings = EditorSettingsExternalizable.getInstance()
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    // Show notification only if:
    // 1. At least DAYS_AFTER_FIRST_RUN days have passed since user's first EAP run
    // 2. Notification has not been shown before
    // 3. Animated caret feature is currently enabled

    val firstRunDate = getFirstEapRunDate() ?: return false
    val targetDate = firstRunDate.plus(DatePeriod(days = DAYS_AFTER_FIRST_RUN))

    return today >= targetDate &&
           !usageStorage.state.feedbackNotificationShown &&
           editorSettings.isSmoothCaretMovement
  }

  override fun checkExtraConditionSatisfiedForAction(project: Project): Boolean {
    // Action is only enabled when animated caret feature is enabled
    val editorSettings = EditorSettingsExternalizable.getInstance()
    return editorSettings.isSmoothCaretMovement || editorSettings.isSmoothBlinkCaret
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      SmoothCaretFeedbackBundle.message("feedback.smooth.caret.notification.title"),
      SmoothCaretFeedbackBundle.message("feedback.smooth.caret.notification.text")
    )
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    SmoothCaretUsageLocalStorage.getInstance().recordFeedbackNotificationShown()
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {
    // No additional state updates needed
  }

  /**
   * Gets the first EAP run date, with registry override support for testing.
   *
   * @return LocalDate of first EAP run, or null if not yet recorded
   */
  private fun getFirstEapRunDate(): LocalDate? {
    // Check registry override first (for testing)
    val registryOverride = Registry.stringValue("smooth.caret.survey.first.run.date")
    if (registryOverride.isNotBlank()) {
      return tryParseDate(registryOverride)
    }

    // Get actual first run date from storage
    val firstRunDateString = SmoothCaretUsageLocalStorage.getInstance().state.firstEapRunDate
    return firstRunDateString?.let { tryParseDate(it) }
  }

  /**
   * Parses ISO-8601 date string to LocalDate.
   *
   * @param dateString ISO-8601 format: "2026-02-10"
   * @return LocalDate or null if parsing fails
   */
  private fun tryParseDate(dateString: String): LocalDate? {
    return try {
      LocalDate.parse(dateString)
    } catch (_: Exception) {
      null
    }
  }
}
