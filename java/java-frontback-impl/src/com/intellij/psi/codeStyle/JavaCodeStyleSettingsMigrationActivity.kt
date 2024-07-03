// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.application.options.CodeStyle
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.codeStyle.CodeStyleScheme.PROJECT_SCHEME_NAME

class JavaCodeStyleSettingsMigrationActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val applicationMigrationState = JavaCodeStyleSettingsApplicationMigrationManager.getInstance().state
    val projectMigrationState = JavaCodeStyleSettingsProjectMigrationManager.getInstance(project).state

    if (!applicationMigrationState.areSchemesMigrated) {
      CodeStyleSchemes.getInstance().allSchemes.forEach { scheme ->
        if (scheme.name != PROJECT_SCHEME_NAME) {
          migrateSettings(scheme.codeStyleSettings)
        }
      }
      applicationMigrationState.areSchemesMigrated = true
    }

    if (!projectMigrationState.areSchemesMigrated) {
      val settings = CodeStyle.getSettings(project)
      migrateSettings(settings)
      projectMigrationState.areSchemesMigrated = true
    }
  }

  private fun migrateSettings(codeStyleSettings: CodeStyleSettings) {
    val commonSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE)
    val customSettings = codeStyleSettings.getCustomSettings(JavaCodeStyleSettings::class.java)
    customSettings.BLANK_LINES_AROUND_FIELD_WITH_ANNOTATIONS = commonSettings.BLANK_LINES_AROUND_FIELD
  }
}