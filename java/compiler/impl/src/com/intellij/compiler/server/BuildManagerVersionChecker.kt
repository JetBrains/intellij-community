// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server

import com.intellij.java.JavaBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.modifySdkEntity
import com.intellij.util.system.CpuArch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jps.model.java.JdkVersionDetector

@Service(Service.Level.PROJECT)
internal class BuildManagerVersionChecker(val project: Project, val scope: CoroutineScope) {
  private var lastWarned: String? = null

  fun checkArch(sdkHome: String?) {
    val home = sdkHome ?: return

    if (lastWarned == home) return
    lastWarned = home

    scope.launch {
      val versionInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(home) ?: return@launch

      if (!ApplicationManager.getApplication().isUnitTestMode) {
        updateVersionStrings(home, versionInfo)
      }

      val jdkArch = versionInfo.arch ?: return@launch

      if (jdkArch != CpuArch.CURRENT) {
        NotificationGroupManager.getInstance()
          .getNotificationGroup("JDK Arch Check")
          .createNotification(
            JavaBundle.message("arch.checker.notification.title"),
            JavaBundle.message("arch.checker.notification.content", versionInfo.displayVersionString(), jdkArch.name, CpuArch.CURRENT.name),
            NotificationType.WARNING
          )
          .apply {
            addAction(NotificationAction.createSimpleExpiring(JavaBundle.message("arch.checker.notification.project.structure")) {
              ProjectSettingsService.getInstance(project).openProjectSettings()
            })
            notify(project)
          }
      }
    }
  }

  private suspend fun updateVersionStrings(home: String, versionInfo: JdkVersionDetector.JdkVersionInfo) {
    val versionString = versionInfo.displayVersionString()

    project.workspaceModel.currentSnapshot.entities(SdkEntity::class.java)
      .filter { it.homePath?.url == home && it.version != versionString }
      .forEach { sdkEntity ->
        project.workspaceModel.update("Updating JDK versions string") { storage ->
          storage.modifySdkEntity(sdkEntity) {
            this.version = versionString
          }
        }
      }
  }
}