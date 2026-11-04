// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Migrates an open project when a light mode is transitioned to the smart mode
 *
 * The upgrade calls [migrateProject] for every open project after it has loaded the smart-mode plugins.
 * The project is still dumb at this point. The upgrade waits for every extension, and then it starts the smart activities.
 */
@ApiStatus.Internal
interface LightModeProjectMigration {
  suspend fun migrateProject(project: Project)

  companion object {
    private val EP_NAME: ExtensionPointName<LightModeProjectMigration> =
      ExtensionPointName("com.intellij.lightModeProjectMigration")

    /**
     * Runs [migrateProject] of every extension for every project in [projects], in parallel, and returns when all have finished.
     * A failed extension is logged. It does not stop the upgrade.
     */
    @ApiStatus.Internal
    suspend fun migrateProjects(projects: List<Project>) {
      val migrations = EP_NAME.extensionList
      coroutineScope {
        for (project in projects) {
          for (migration in migrations) {
            launch {
              if (project.isDisposed) return@launch
              try {
                migration.migrateProject(project)
              }
              catch (e: Throwable) {
                rethrowControlFlowException(e)
                logger<LightModeProjectMigration>().error("$migration failed for '${project.name}'", e)
              }
            }
          }
        }
      }
    }
  }
}
