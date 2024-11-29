// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.java.JavaBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.lang.JavaVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class JavaVersionChecker: RunConfigurationExtension() {
  override fun <T : RunConfigurationBase<*>?> updateJavaParameters(configuration: T & Any, params: JavaParameters, runnerSettings: RunnerSettings?) {
    val jdk = params.jdk ?: return
    val checkerService = configuration.project.service<JavaVersionCheckerService>()
    checkerService.checkJdk(jdk)
  }

  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
    return configuration is JavaRunConfigurationBase
  }
}

@Service(Service.Level.PROJECT)
private class JavaVersionCheckerService(private val project: Project, private val coroutineScope: CoroutineScope) {
  val checked = hashSetOf<String>()

  fun checkJdk(jdk: Sdk) {
    val path = jdk.homePath ?: return
    if (!checked.add(path)) return

    coroutineScope.launch {
      val version = JavaVersion.tryParse(jdk.versionString) ?: return@launch
      if (!version.isAtLeast(8)) {
        NotificationGroupManager.getInstance()
          .getNotificationGroup("Unsupported JDK")
          .createNotification(JavaBundle.message("unsupported.jdk.notification.title"), JavaBundle.message("unsupported.jdk.notification.content", jdk.name), NotificationType.WARNING)
          .notify(project)
      }
    }
  }

}