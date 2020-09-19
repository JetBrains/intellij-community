// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.projectRoots.impl.UnknownSdkCollector
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.text.VersionComparatorUtil

internal class JdkUpdaterStartup : StartupActivity.Background {
  override fun runActivity(project: Project) {
    UnknownSdkCollector(project).collectSdksPromise { snapshot ->
      if (snapshot.knownSdks.isEmpty()) return@collectSdksPromise

      val jdkFeed = JdkListDownloader.getInstance().downloadForUI(progress = null)
        .associateBy { it.suggestedSdkName }

      snapshot.knownSdks
        .filter { it.sdkType is JavaSdkType && it.sdkType !is DependentSdkType }
        .forEach { jdk ->
          val actualItem = JdkInstaller.getInstance().findJdkItemForInstalledJdk(jdk.homePath) ?: return@forEach
          val feedItem = jdkFeed[actualItem.suggestedSdkName] ?: return@forEach
          if (VersionComparatorUtil.compare(feedItem.jdkVersion, actualItem.jdkVersion) <= 0) return@forEach

          val title = "Update JDK '" + jdk.name + "' to " + feedItem.fullPresentationText
          val message = "The current version of the JDK is " + actualItem.jdkVersion + ", would you like to update?"

          NotificationGroupManager.getInstance().getNotificationGroup("Update JDK")
            .createNotification(title, message, NotificationType.INFORMATION, null)
            .setImportant(true)
            .addAction(NotificationAction.createSimple(
              "Download " + feedItem.fullPresentationText,
              Runnable { updateJdk(project, jdk, feedItem) }))
            .addAction(NotificationAction.createSimple(
              "Cancel",
              Runnable { TODO("remember we've cancelled an ide") }))
            .notify(project)
        }
    }
  }

  private fun updateJdk(project: Project, jdk: Sdk, feedItem: JdkItem) {
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, "Updating JDK '${jdk.name}' to ${feedItem.fullPresentationText}", true, ALWAYS_BACKGROUND) {
        override fun run(indicator: ProgressIndicator) {
          val installer = JdkInstaller.getInstance()
          val prepare = installer.prepareJdkInstallation(feedItem, installer.defaultInstallDir(feedItem))
          installer.installJdk(prepare, indicator, project)

          runWriteAction {
            jdk.sdkModificator.apply {
              removeAllRoots()
              homePath = prepare.javaHome.systemIndependentPath
            }.commitChanges()

            val sdkType = SdkType
                            .getAllTypes()
                            .singleOrNull(SimpleJavaSdkType.notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType()::value)
                          ?: return@runWriteAction null

            sdkType.setupSdkPaths(jdk)
          }
        }
      }
    )
  }
}
