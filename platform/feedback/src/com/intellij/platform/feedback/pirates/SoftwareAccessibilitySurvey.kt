// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.pirates

import com.intellij.ide.ConsentOptionsProvider
import com.intellij.internal.statistic.collectors.fus.os.getAgentMetrics
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.ExternalFeedbackSurveyConfig
import com.intellij.platform.feedback.ExternalFeedbackSurveyType
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.FeedbackSurveyType
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import com.intellij.util.io.URLUtil.encodeURIComponent
import com.intellij.util.system.OS
import com.intellij.util.withQuery
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import java.net.URI

internal class SoftwareAccessibilitySurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> = ExternalFeedbackSurveyType(SoftwareAccessibilitySurveyConfig())
}

internal class ShowSoftwareAccessibilitySurveyAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      SoftwareAccessibilitySurvey().showNotification(project, false)
    }
  }
}

private class SoftwareAccessibilitySurveyConfig : ExternalFeedbackSurveyConfig {
  override val surveyId: String = "external_software_accessibility_survey"
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2025, Month.JANUARY, 1)
  override val requireIdeEAP: Boolean = false

  override fun checkIdeIsSuitable(): Boolean {
    return PlatformUtils.isCommercialEdition()
           && !service<ConsentOptionsProvider>().isActivatedWithFreeLicense
           && !ApplicationInfo.getInstance().isEAP
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    return getAgentMetrics().isAddOpensPresent1
  }

  override fun getUrlToSurvey(project: Project): String {
    val uri = URI.create("https://surveys.jetbrains.com/s3/jetbrains-ide-experience-survey")

    val os = encodeURIComponent(OS.CURRENT.toString().lowercase())
    val country = encodeURIComponent(System.getProperty("user.country", "unknown"))
    val lang = encodeURIComponent(System.getProperty("user.language", "unknown"))
    val version = encodeURIComponent(ApplicationInfo.getInstance().fullVersion)
    val product = encodeURIComponent(ApplicationInfoImpl.getShadowInstanceImpl().build.productCode)

    val url = uri.withQuery("os=${os}&country=${country}&lang=${lang}&product=${product}&ver=${version}")
    return url.toString()
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      SoftwareAccessibilitySurveyBundle.message("notification.pirates.request.title"),
      SoftwareAccessibilitySurveyBundle.message("notification.pirates.request.content")
    )
  }

  override fun updateStateAfterRespondActionInvoked(project: Project) {
    // do nothing
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    // do nothing
  }
}