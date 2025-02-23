// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.csat

import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

internal class CsatNewUserTracker : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (ConfigImportHelper.isNewUser()) {
      val settings = CsatGlobalSettings.getInstance()
      if (settings.newUserCreatedAt == null) {
        settings.newUserCreatedAt = LocalDate.now().format(ISO_LOCAL_DATE)
      }
    }
  }
}

internal fun getCsatUserCreatedDate(): LocalDate? {
  val mocked = Registry.stringValue("csat.survey.user.created.date")
  if (!mocked.isBlank()) {
    return tryParseDate(mocked)
  }

  val date = CsatGlobalSettings.getInstance().newUserCreatedAt ?: return null
  return tryParseDate(date)
}