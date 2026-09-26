// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.csat

import com.intellij.frontend.HostIdeInfoService
import com.intellij.ide.Prefs
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.service

private const val CSAT_NEW_USER_CREATED_AT_KEY = "csat.user.created.at"
private const val CSAT_SURVEY_LAST_FEEDBACK_DATE_KEY = "csat.survey.last.feedback.date"
private const val CSAT_SURVEY_LAST_NOTIFICATION_DATE_KEY = "csat.survey.last.notification.date"

internal class CsatGlobalSettings private constructor(
  private val productCode: String,
) {
  companion object {
    fun getInstance(): CsatGlobalSettings {
      return CsatGlobalSettings(service<HostIdeInfoService>().getHostInfo()?.productCode
                                ?: ApplicationInfo.getInstance().build.productCode)
    }
  }

  private fun productKey(keyName: String): String = "JetBrains.$productCode.$keyName"

  var lastFeedbackDate: String?
    get() = Prefs.get(productKey(CSAT_SURVEY_LAST_FEEDBACK_DATE_KEY), null)
    set(value) {
      val key = productKey(CSAT_SURVEY_LAST_FEEDBACK_DATE_KEY)
      Prefs.put(key, value)
      Prefs.flush(key)
    }

  var lastNotificationDate: String?
    get() = Prefs.get(productKey(CSAT_SURVEY_LAST_NOTIFICATION_DATE_KEY), null)
    set(value) {
      val key = productKey(CSAT_SURVEY_LAST_NOTIFICATION_DATE_KEY)
      Prefs.put(key, value)
      Prefs.flush(key)
    }

  var newUserCreatedAt: String?
    get() = Prefs.get(productKey(CSAT_NEW_USER_CREATED_AT_KEY), null)
    set(value) {
      val key = productKey(CSAT_NEW_USER_CREATED_AT_KEY)
      Prefs.put(key, value)
      Prefs.flush(key)
    }
}
