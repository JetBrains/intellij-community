// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.csat

import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.InIdeFeedbackSurveyType
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

internal const val USER_CONSIDERED_NEW_DAYS = 30
internal const val NEW_USER_SURVEY_PERIOD = 29
internal const val EXISTING_USER_SURVEY_PERIOD = 97
internal const val CSAT_SURVEY_LAST_FEEDBACK_DATE_KEY = "csat.survey.last.feedback.date"

internal class CsatFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: InIdeFeedbackSurveyType<InIdeFeedbackSurveyConfig> =
    InIdeFeedbackSurveyType(CsatFeedbackSurveyConfig())
}

private val LOG = Logger.getInstance(CsatFeedbackSurvey::class.java)

internal class CsatFeedbackSurveyConfig : InIdeFeedbackSurveyConfig {

  override val surveyId: String = "csat_feedback"
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2050, Month.JANUARY, 1)
  override val requireIdeEAP: Boolean = false

  override fun checkIdeIsSuitable(): Boolean = Registry.`is`("csat.survey.enabled")

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return CsatFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {
    PropertiesComponent.getInstance().setValue(CSAT_SURVEY_LAST_FEEDBACK_DATE_KEY, getCsatToday().format(DateTimeFormatter.ISO_LOCAL_DATE))
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    if (ConfigImportHelper.isFirstSession()) return false

    val today = getCsatToday()
    LOG.debug("Today is ${today.format(DateTimeFormatter.ISO_LOCAL_DATE)}")

    val lastFeedbackDate = PropertiesComponent.getInstance().getValue(CSAT_SURVEY_LAST_FEEDBACK_DATE_KEY)
      ?.let { java.time.LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
    if (lastFeedbackDate != null && lastFeedbackDate.plusDays(EXISTING_USER_SURVEY_PERIOD.toLong()).isAfter(today)) {
      LOG.debug("User recently filled the survey, vacation period is in progress")
      return false
    }

    val userCreatedDate = getCsatUserCreatedDate()
    LOG.debug("User created date is $userCreatedDate")

    val isNewUser = userCreatedDate?.let { isNewUser(today, userCreatedDate) } ?: false
    if (isNewUser) {
      LOG.debug("User is a new user")
    }

    val surveyPeriod = if (isNewUser) NEW_USER_SURVEY_PERIOD else EXISTING_USER_SURVEY_PERIOD

    val productHash = abs((ApplicationInfo.getInstance().versionName + MachineIdManager.getAnonymizedMachineId("CSAT Survey")).hashCode()) % surveyPeriod
    val daysHash = abs(ChronoUnit.DAYS.between(java.time.LocalDate.of(1970, 1, 1), today).toInt()) % surveyPeriod

    if (productHash != daysHash) {
      LOG.debug("Periods do not match: $productHash / $daysHash, is not yet suitable date for the survey")
      return false // not the day we need
    }

    val show = flipACoin(ApplicationInfo.getInstance().build.productCode, isNewUser)
    if (!show) {
      LOG.debug("Coin flipped to NOT show the survey this time")
    }

    return show
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      CsatFeedbackBundle.message("feedback.notification.title"),
      CsatFeedbackBundle.message("feedback.notification.text", ApplicationInfo.getInstance().versionName)
    )
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
  }
}

internal fun getCsatToday(): java.time.LocalDate {
  try {
    Registry.stringValue("csat.survey.today")
      .takeIf { it.isNotBlank() }
      ?.let { java.time.LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
      ?.let { return it }
  }
  catch (e: Exception) {
    fileLogger().error(e)
  }

  return java.time.LocalDate.now()
}

internal fun flipACoin(productCode: String, newUser: Boolean): Boolean {
  val probability: Double? = Registry.stringValue("csat.survey.show.probability")
    .takeIf { it.isNotBlank() }
    ?.toDoubleOrNull()

  if (probability != null) {
    when {
      probability <= 0.0 -> {
        return false
      }
      probability >= 1.0 -> {
        return true
      }
      else -> {
        return Math.random() < probability
      }
    }
  }

  val probabilityPerProduct: Double =
    if (newUser) {
      when (productCode) {
        "PY", "IC", "IU" -> 0.125
        "PC" -> 0.07
        else -> 1.0
      }
    }
    else {
      when (productCode) {
        "PC", "IU" -> 0.015
        "IC", "PY" -> 0.025
        else -> 0.125
      }
    }

  if (probabilityPerProduct >= 1.0) return true

  return Math.random() < probabilityPerProduct
}

internal fun isNewUser(today: java.time.LocalDate, userCreatedDate: java.time.LocalDate): Boolean {
  return today.isBefore(userCreatedDate.plusDays(USER_CONSIDERED_NEW_DAYS.toLong()))
}