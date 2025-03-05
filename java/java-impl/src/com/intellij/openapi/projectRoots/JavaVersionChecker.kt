// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots

import com.intellij.java.JavaBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.startup.ProjectActivity

/**
 * Project activity that reports unsupported JDKs used in the project.
 */
private class JavaVersionChecker: ProjectActivity {

  override suspend fun execute(project: Project) {
    val projectSdk = ProjectRootManager.getInstance(project).projectSdk

    if (isJdkUnsupported(projectSdk, project, null)) return

    for (module in ModuleManager.getInstance(project).modules) {
      val manager = ModuleRootManager.getInstance(module)
      if (manager.isSdkInherited) continue
      if (isJdkUnsupported(manager.sdk, project, module)) return
    }
  }

  private fun isJdkUnsupported(sdk: Sdk?, project: Project, module: Module?): Boolean {
    if (sdk == null) return false
    if (sdk.sdkType !is JavaSdkType) return false

    val version = JavaSdk.getInstance().getVersion(sdk) ?: return false
    if (version.isAtLeast(JavaSdkVersion.JDK_1_8)) return false

    NotificationGroupManager.getInstance()
      .getNotificationGroup("Unsupported JDK")
      .createNotification(
        JavaBundle.message("unsupported.jdk.notification.title"),
        JavaBundle.message("unsupported.jdk.notification.content", sdk.name),
        NotificationType.WARNING
      )
      .addAction(NotificationAction.createSimpleExpiring(ProjectBundle.message("action.text.config.unknown.sdk.configure")) {
        showSettingsFor(project, module)
      })
      .notify(project)

    return true
  }

  private fun showSettingsFor(project: Project, module: Module?) {
    when (module) {
      null -> ProjectSettingsService.getInstance(project).openProjectSettings()
      else -> ProjectSettingsService.getInstance(project).openModuleDependenciesSettings(module, null)
    }
  }
}