// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.upgrade

import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.FeedbackSurveyType
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.InIdeFeedbackSurveyType
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

internal class UpgradeFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<InIdeFeedbackSurveyConfig> =
    InIdeFeedbackSurveyType(UpgradeFeedbackSurveyConfig())
}

/** Set by `IdeUpdateToolbarWidget` when the user starts an update from the header button or the gear menu. */
private const val UPDATE_WIDGET_CLICKED_KEY: String = "ide.update.toolbar.widget.clicked"

/** The build for which the survey notification was shown. */
private const val SURVEY_SHOWN_FOR_BUILD_KEY: String = "upgrade.feedback.survey.shown.for.build"

/**
 * Shows the survey to a user who updated the IDE with the Update button.
 * The survey shows once on the current build.
 */
internal class UpgradeFeedbackSurveyConfig : InIdeFeedbackSurveyConfig {
  override val surveyId: String = "upgrade_feedback"
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2027, Month.APRIL, 1)
  override val requireIdeEAP: Boolean = false

  override fun checkIdeIsSuitable(): Boolean {
    return Registry.`is`("upgrade.feedback.survey.enabled", true)
           && !AppMode.isRemoteDevHost()
           && !ScreenReader.isActive()
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    val properties = PropertiesComponent.getInstance()
    return properties.getBoolean(UPDATE_WIDGET_CLICKED_KEY)
           && properties.getValue(SURVEY_SHOWN_FOR_BUILD_KEY) != currentBuild()
  }

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return UpgradeFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {
    // do nothing
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      UpgradeFeedbackBundle.message("upgrade.feedback.notification.title"),
      UpgradeFeedbackBundle.message("upgrade.feedback.notification.text", ApplicationNamesInfo.getInstance().fullProductName),
    )
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    PropertiesComponent.getInstance().setValue(SURVEY_SHOWN_FOR_BUILD_KEY, currentBuild())
  }

  private fun currentBuild(): String = ApplicationInfo.getInstance().build.asString()
}
