// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.impl

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.*
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker.*
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.roots.ui.configuration.UnknownSdk
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix
import com.intellij.ui.components.dialog
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.layout.*
import java.awt.Dimension

@Service
class CompilerDriverUnknownSdkTracker(
  private val project: Project
) {
  companion object {
    val LOG = logger<CompilerDriverUnknownSdkTracker>()

    @JvmStatic
    fun getInstance(project: Project) = project.service<CompilerDriverUnknownSdkTracker>()
  }

  fun fixSdkSettings(updateProjectSdk: Boolean,
                     modules: List<Module>) {
    val collector = object: UnknownSdkCollector(project) {
      override fun checkProjectSdk(project: Project): Boolean = updateProjectSdk
      override fun collectModulesToCheckSdk(project: Project) = modules
    }

    UnknownSdkTracker.getInstance(project).updateUnknownSdksBlocking(collector, object: ShowStatusCallback {
      override fun showStatus(unknownSdksWithoutFix: List<UnknownSdk>,
                              localFixes: Map<UnknownSdk, UnknownSdkLocalSdkFix>,
                              downloadFixes: Map<UnknownSdk, UnknownSdkDownloadableSdkFix>,
                              invalidSdks: List<UnknownInvalidSdk>) = invokeLater {

        val userActions = UnknownSdkEditorNotification
          .getInstance(project).buildNotifications(unknownSdksWithoutFix, downloadFixes, invalidSdks)

        val notifications = UnknownSdkBalloonNotification.getInstance(project).buildNotifications(localFixes);

        if (userActions.isEmpty() && notifications == null) {
          return@invokeLater
        }

        if (userActions.isEmpty() && notifications != null) {
          //TODO: we need a modal dialog to ask them to restart the action.
          //TODO: we need to open Event Log for further interactions
          UnknownSdkBalloonNotification.getInstance(project).buildNotifications(localFixes)
          return@invokeLater
        }

        dialog(
          project = project,
          title = "Resolve SDK",
          panel = panel {
            userActions.forEach {
              row {
                NonOpaquePanel(it.createNotificationPanel(project)).invoke(CCFlags.growY).component.minimumSize = Dimension(100, 100)
              }
            }

            notifications?.usages?.forEach {
              row {
                label(it)
              }
            }

            row {
              link("Open Settings") {
                ProjectSettingsService.getInstance(project).openProjectSettings()
              }
            }
          }
        ).showAndGet()
      }
    })
  }
}
