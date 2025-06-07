// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.csat

import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.currentSessionOrNull
import com.intellij.openapi.client.sessions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.InIdeFeedbackSurveyType
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.abs

internal const val USER_CONSIDERED_NEW_DAYS = 30
internal const val NEW_USER_SURVEY_PERIOD = 29
internal const val EXISTING_USER_SURVEY_PERIOD = 97

internal class CsatFeedbackSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: InIdeFeedbackSurveyType<InIdeFeedbackSurveyConfig> =
    InIdeFeedbackSurveyType(CsatFeedbackSurveyConfig())
}

private val LOG = Logger.getInstance(CsatFeedbackSurvey::class.java)

internal class CsatFeedbackSurveyConfig : InIdeFeedbackSurveyConfig {

  override val surveyId: String = "csat_feedback"
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2050, Month.JANUARY, 1)
  override val requireIdeEAP: Boolean = false
  override val isIndefinite: Boolean = true

  override fun checkIdeIsSuitable(): Boolean {
    return Registry.`is`("csat.survey.enabled")
           && !AppMode.isRemoteDevHost()
           && !ScreenReader.isActive()
  }

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return CsatFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {
    CsatGlobalSettings.getInstance().lastFeedbackDate = getCsatToday().format(DateTimeFormatter.ISO_LOCAL_DATE)
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    if (project.currentSessionOrNull?.isGuest == true) {
      LOG.debug("We are a CWM guest, do not really need CSAT")
      return false
    }
    if (project.sessions(ClientKind.GUEST).isNotEmpty()) {
      LOG.debug("We are the CWM host at the moment, not the perfect time for CSAT")
      return false
    }

    if (ConfigImportHelper.isFirstSession()) {
      LOG.debug("It's a first user session, skip the survey")
      return false
    }

    val today = getCsatToday()
    LOG.debug("Today is ${today.format(DateTimeFormatter.ISO_LOCAL_DATE)}")

    CsatGlobalSettings.getInstance().lastNotificationDate
      ?.let { tryParseDate(it) }
      ?.let {
        if (it.isEqual(today)) {
          LOG.debug("Already notified today, skip the survey")
          return false
        }
      }

    val lastFeedbackDate = CsatGlobalSettings.getInstance().lastFeedbackDate
      ?.let { tryParseDate(it) }
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

    val surveyPeriod = getSurveyPeriod(isNewUser)
    val productHash = getProductHash(surveyPeriod)
    val daysHash = getDaysHash(today, surveyPeriod)

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
    CsatGlobalSettings.getInstance().lastNotificationDate = getCsatToday().format(DateTimeFormatter.ISO_LOCAL_DATE)

    // disable an automatic Evaluate Feedback form so we don't have them both shown
    PropertiesComponent.getInstance().setValue("evaluation.feedback.enabled", "false")
  }
}

private fun getDaysHash(today: java.time.LocalDate, surveyPeriod: Int): Int {
  return abs(ChronoUnit.DAYS.between(java.time.LocalDate.of(1970, 1, 1), today).toInt()) % surveyPeriod
}

private fun getProductHash(surveyPeriod: Int): Int {
  return abs((ApplicationInfo.getInstance().versionName + MachineIdManager.getAnonymizedMachineId("CSAT Survey")).hashCode()) % surveyPeriod
}

internal data class NextDate(
  val isNewUser: Boolean,
  val date: java.time.LocalDate
)

internal fun getNextCsatDay(): NextDate {
  val today = getCsatToday()
  val userCreatedDate = getCsatUserCreatedDate()

  val isNewUser = userCreatedDate?.let { isNewUser(today, userCreatedDate) } ?: false
  val surveyPeriod = getSurveyPeriod(isNewUser)

  for (i in 0..364) {
    val date = today.plusDays(i.toLong())

    val productHash = getProductHash(surveyPeriod)
    val daysHash = getDaysHash(date, surveyPeriod)

    if (productHash == daysHash) {
      return NextDate(isNewUser, date)
    }
  }

  throw IllegalStateException("No suitable date found")
}

private fun getSurveyPeriod(isNewUser: Boolean): Int {
  return if (isNewUser) NEW_USER_SURVEY_PERIOD else EXISTING_USER_SURVEY_PERIOD
}

internal fun getCsatToday(): java.time.LocalDate {
  Registry.stringValue("csat.survey.today")
    .takeIf { it.isNotBlank() }
    ?.let { tryParseDate(it) }
    ?.let { return it }

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

internal fun tryParseDate(it: String): java.time.LocalDate? {
  return try {
    java.time.LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
  }
  catch (_: DateTimeParseException) {
    return null
  }
}