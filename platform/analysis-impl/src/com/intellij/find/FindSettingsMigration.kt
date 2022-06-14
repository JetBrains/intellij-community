// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.search.scope.ProjectFilesScope
import com.intellij.util.application

internal class FindSettingsMigration : StartupActivity {
  companion object {
    private val logger = logger<FindSettingsMigration>()
  }

  override fun runActivity(project: Project) {
    // Because of some reason, starting from 20.1 in FindSettings as a default scope "All classes" were stored.
    // Now we need to fix it and get back default one - Project files
    val migrationSettings = application.getService(MigrationSettings::class.java)
    if (!migrationSettings.state.alreadyApplied) {
      migrationSettings.state.alreadyApplied = true
      val settings = FindSettings.getInstance()
      if (settings.defaultScopeName == "All Places") {
        settings.defaultScopeName = ProjectFilesScope.INSTANCE.scopeId
        logger.info("Applied migration to 'Project files' scope")
      }
    }
  }


  @Service(Service.Level.APP)
  @State(name = "find settings migration", storages = [Storage("migration.xml")])
  class MigrationSettings : SimplePersistentStateComponent<MigrationState>(MigrationState())

  class MigrationState : BaseState() {
    var alreadyApplied by property(false)
  }
}