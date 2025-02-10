// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.csat

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal const val CSAT_NEW_USER_CREATED_AT_PROPERTY = "csat.user.created.at"

internal val CSAT_TS_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE

internal class CsatNewUserTracker : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (ConfigImportHelper.isNewUser()) {
      val propertiesComponent = PropertiesComponent.getInstance()

      if (!propertiesComponent.isValueSet(CSAT_NEW_USER_CREATED_AT_PROPERTY)) {
        propertiesComponent.setValue(CSAT_NEW_USER_CREATED_AT_PROPERTY,
                                     LocalDate.now().format(CSAT_TS_FORMAT))
      }
    }
  }
}

internal fun getCsatUserCreatedDate(): LocalDate? {
  val mocked = Registry.stringValue("csat.survey.user.created.date")
  if (!mocked.isBlank()) {
    return try {
      LocalDate.parse(mocked, CSAT_TS_FORMAT)
    }
    catch (_: DateTimeParseException) {
      null
    }
  }

  val date = PropertiesComponent.getInstance().getValue(CSAT_NEW_USER_CREATED_AT_PROPERTY) ?: return null
  return try {
    LocalDate.parse(date, CSAT_TS_FORMAT)
  }
  catch (_: DateTimeParseException) {
    null
  }
}