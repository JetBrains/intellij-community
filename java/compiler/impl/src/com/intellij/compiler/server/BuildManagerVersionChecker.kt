// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server

import com.intellij.java.JavaBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.util.system.CpuArch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jps.model.java.JdkVersionDetector

@Service(Service.Level.PROJECT)
class BuildManagerVersionChecker(val project: Project, val scope: CoroutineScope) {
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
        val notification = NotificationGroupManager.getInstance()
          .getNotificationGroup("JDK Arch Check")
          .createNotification(JavaBundle.message("arch.checker.notification.title"), JavaBundle.message("arch.checker.notification.content"), NotificationType.WARNING)

        notification.apply {
          addAction(object : NotificationAction(JavaBundle.message("arch.checker.notification.project.structure")) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
              ActionManager.getInstance().getAction("ShowProjectStructureSettings").actionPerformed(e)
              notification.expire()
            }
          })

          notify(project)
        }
      }
    }
  }

  private suspend fun updateVersionStrings(home: String, versionInfo: JdkVersionDetector.JdkVersionInfo) {
    ProjectJdkTable.getInstance().allJdks.filter { it.homePath == home }.forEach { jdk ->
      val versionString = versionInfo.displayVersionString()
      if (jdk.versionString != versionString) {
        writeAction {
          val modificator = jdk.sdkModificator
          modificator.versionString = versionString
          modificator.commitChanges()
        }
      }
    }
  }
}