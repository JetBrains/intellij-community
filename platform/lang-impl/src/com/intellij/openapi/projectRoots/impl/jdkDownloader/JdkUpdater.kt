// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.projectRoots.impl.UnknownSdkCollector
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.text.VersionComparatorUtil
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class JdkUpdaterStateData : BaseState() {
  val dndVersions by stringSet()
}

internal class JdkUpdaterStartup : StartupActivity.Background {
  override fun runActivity(project: Project) {
    project.service<JdkUpdater>().updateNotifications()
  }
}

internal class JdkUpdaterState : SimplePersistentStateComponent<JdkUpdaterStateData>(JdkUpdaterStateData()) {
  private val lock = ReentrantLock()

  override fun loadState(state: JdkUpdaterStateData) = lock.withLock {
    super.loadState(state)
  }

  fun isAllowed(feedItem: JdkItem) = lock.withLock {
    feedItem.fullPresentationText !in state.dndVersions
  }

  fun blockVersion(feedItem: JdkItem) = lock.withLock {
    state.dndVersions += feedItem.fullPresentationText
    state.intIncrementModificationCount()
  }
}

@Service
internal class JdkUpdater(
  private val project: Project
) : Disposable {
  override fun dispose() = Unit

  init {
    val future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
      Runnable {
        if (!project.isDisposed) {
          updateNotifications()
        }
      },
      12,
      12,
      TimeUnit.HOURS
    )
    Disposer.register(this, Disposable { future.cancel(false) })
  }

  fun updateNotifications() {
    UnknownSdkCollector(project).collectSdksPromise { snapshot ->
      val knownSdks = snapshot
        .knownSdks
        .filter { it.sdkType is JavaSdkType && it.sdkType !is DependentSdkType }

      if (knownSdks.isEmpty()) return@collectSdksPromise

      ProgressManager.getInstance().run(
        object : Task.Backgroundable(project, "Checking for JDK updates", true, ALWAYS_BACKGROUND) {
          override fun run(indicator: ProgressIndicator) {
            updateWithSnapshot(knownSdks, indicator)
          }
        }
      )
    }
  }

  private fun updateWithSnapshot(knownSdks: List<Sdk>, indicator: ProgressIndicator) {
    val jdkFeed = JdkListDownloader.getInstance().downloadForUI(progress = indicator)
      .associateBy { it.suggestedSdkName }

    knownSdks
      .forEach { jdk ->
        val actualItem = JdkInstaller.getInstance().findJdkItemForInstalledJdk(jdk.homePath) ?: return@forEach
        val feedItem = jdkFeed[actualItem.suggestedSdkName] ?: return@forEach
        if (!service<JdkUpdaterState>().isAllowed(feedItem)) return@forEach
        if (VersionComparatorUtil.compare(feedItem.jdkVersion, actualItem.jdkVersion) <= 0) return@forEach

        val title = "Update JDK '" + jdk.name + "' to " + feedItem.fullPresentationText
        val message = "The current version of the JDK is " + actualItem.jdkVersion + ", would you like to update?"

        NotificationGroupManager.getInstance().getNotificationGroup("Update JDK")
          .createNotification(title, message, NotificationType.INFORMATION, null)
          .setImportant(true)
          .addAction(NotificationAction.createSimple(
            "Download " + feedItem.fullPresentationText + "...",
            Runnable { updateJdk(project, jdk, feedItem) }))
          .addAction(NotificationAction.createSimple(
            "Skip this update",
            Runnable {
              service<JdkUpdaterState>().blockVersion(feedItem)
            }))
          .notify(project)
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
