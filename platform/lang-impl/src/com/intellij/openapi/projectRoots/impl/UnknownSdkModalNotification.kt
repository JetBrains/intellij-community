// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.roots.ui.configuration.UnknownSdk
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix
import com.intellij.openapi.roots.ui.configuration.UnknownSdkLocalSdkFix
import com.intellij.ui.components.dialog
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.layout.*
import java.awt.Dimension

@Service
class UnknownSdkModalNotification(private val project: Project) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<UnknownSdkModalNotification>()
  }

  enum class Outcome {
    NO_CHANGES,
    CONFIGURED
  }

  fun showNotifications(unknownSdksWithoutFix: List<UnknownSdk>,
                        localFixes: Map<UnknownSdk, UnknownSdkLocalSdkFix>,
                        downloadFixes: Map<UnknownSdk, UnknownSdkDownloadableSdkFix>,
                        invalidSdks: List<UnknownInvalidSdk>
  ): Outcome {
    val userActions = UnknownSdkEditorNotification.getInstance(project).buildNotifications(unknownSdksWithoutFix, downloadFixes,
                                                                                           invalidSdks)
    val notifications = UnknownSdkBalloonNotification.getInstance(project).buildNotifications(localFixes);

    if (userActions.isEmpty() && notifications == null) {
      return Outcome.NO_CHANGES
    }

    if (userActions.isEmpty() && notifications != null) {
      //TODO: we need a modal dialog to ask them to restart the action.
      //TODO: we need to open Event Log for further interactions
      UnknownSdkBalloonNotification.getInstance(project).buildNotifications(localFixes)
      return Outcome.CONFIGURED
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

    ///TODO: should we re-check it here to see if the problem was resolved?
    ///TODO: we need modal+backgroundable JDK download (if it took place here)
    return Outcome.CONFIGURED
  }
}
