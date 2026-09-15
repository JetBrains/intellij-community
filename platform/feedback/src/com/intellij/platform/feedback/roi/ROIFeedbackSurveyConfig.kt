// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.roi

import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.ExternalFeedbackSurveyConfig
import com.intellij.platform.feedback.ExternalFeedbackSurveyType
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.FeedbackSurveyType
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.ui.LicensingFacade
import com.intellij.util.PlatformUtils
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

internal class ROIFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> =
    ExternalFeedbackSurveyType(ROIFeedbackSurveyConfig())
}

private const val URL_TO_DEVELOPER_EXPERIENCE_SURVEY = "https://surveys.jetbrains.com/s3/intellij-idea-developer-experience-survey"

internal class ROIFeedbackSurveyConfig : ExternalFeedbackSurveyConfig {

  override val surveyId: String = "feedback_survey_for_roi_calculation"

  override val lastDayOfFeedbackCollection: LocalDate =
    LocalDate(2026, Month.OCTOBER, 19) // 2026.2.4 is scheduled for 13 October 2026. Duration is 1 week.

  override val requireIdeEAP: Boolean = false // Should be shown in 2026.2.4 release

  private val suitableIdeVersion = "2026.2.4"

  private val unsuitableRegions = listOf(Region.AFRICA, Region.CHINA)

  override fun checkIdeIsSuitable(): Boolean {
    // IntelliJ IDEA Ultimate subscribers only
    return PlatformUtils.isIdeaUltimate() && hasPaidUltimateSubscription()
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    return ApplicationInfo.getInstance().fullVersion.startsWith(suitableIdeVersion) &&
           RegionSettings.getRegion() !in unsuitableRegions
  }

  override fun createNotification(
    project: Project,
    forTest: Boolean,
  ): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      ROIFeedbackBundle.message("feedback.roi.notification.title"),
      ROIFeedbackBundle.message("feedback.roi.notification.text")
    )
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
  }

  override fun getUrlToSurvey(project: Project): String {
    return URL_TO_DEVELOPER_EXPERIENCE_SURVEY
  }

  override fun updateStateAfterRespondActionInvoked(project: Project) {
  }

  override fun getRespondNotificationActionLabel(): String {
    return ROIFeedbackBundle.message("feedback.roi.notification.respond")
  }

  private fun hasPaidUltimateSubscription(): Boolean {
    if (PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)) {
      return false
    }
    val facade = LicensingFacade.getInstance() ?: return false
    return facade.getConfirmationStamp(facade.platformProductCode) != null
           && !facade.isEvaluationLicense // not trial
  }
}
