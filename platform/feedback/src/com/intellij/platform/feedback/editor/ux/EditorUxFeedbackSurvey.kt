// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.editor.ux

import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.ExternalFeedbackSurveyConfig
import com.intellij.platform.feedback.ExternalFeedbackSurveyType
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.FeedbackSurveyType
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import com.intellij.util.system.OS
import kotlinx.datetime.LocalDate
import java.time.Month

internal class EditorUxFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: FeedbackSurveyType<*> = ExternalFeedbackSurveyType(EditorUxFeedbackSurveyConfig())
}

private const val BASE_URL = "https://survey.alchemer.com/s3/8762090/Editor-Survey"

internal class EditorUxFeedbackSurveyConfig : ExternalFeedbackSurveyConfig {
  override val surveyId: String = "editor_ux_feedback"
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2026, Month.AUGUST, 1)
  override val requireIdeEAP: Boolean = false

  override fun checkIdeIsSuitable(): Boolean = getProductParam() != null

  override fun getUrlToSurvey(project: Project): String {
    val product = getProductParam() ?: return BASE_URL
    return "$BASE_URL?product=$product&os=${getOsParam()}"
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    if (!Registry.`is`("editor.ux.survey.enabled")) return false

    val codeNumber = MachineIdManager.getAnonymizedMachineId("Editor UX Survey").hashCode()
    return codeNumber % 10 == 0 || System.getProperty("editor.ux.survey.target.user") == "true"
  }

  override fun updateStateAfterRespondActionInvoked(project: Project): Unit = Unit
  override fun updateStateAfterNotificationShowed(project: Project): Unit = Unit

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      EditorUxFeedbackBundle.message("notification.request.title"),
      EditorUxFeedbackBundle.message("notification.request.content"),
    )
  }

  private fun getProductParam(): String? {
    return when {
      PlatformUtils.isIntelliJ() -> "IntelliJ+IDEA"
      PlatformUtils.isPyCharm() -> "PyCharm"
      PlatformUtils.isWebStorm() -> "WebStorm"
      PlatformUtils.isRubyMine() -> "RubyMine"
      PlatformUtils.isPhpStorm() -> "PhpStorm"
      PlatformUtils.isGoIde() -> "GoLand"
      PlatformUtils.isCLion() -> "CLion"
      PlatformUtils.isRider() -> "Rider"
      PlatformUtils.isRustRover() -> "RustRover"
      PlatformUtils.isDataGrip() -> "DataGrip"
      else -> null
    }
  }

  @Suppress("OPT_IN_USAGE")
  private fun getOsParam(): String {
    return when (OS.CURRENT) {
      OS.Windows -> "windows"
      OS.macOS -> "mac"
      else -> "linux"
    }
  }
}
