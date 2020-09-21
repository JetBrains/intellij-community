// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.projectRoots.impl.UnknownSdkCollector
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.xmlb.annotations.OptionTag
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class JdkUpdaterStateData : BaseState() {
  @get:OptionTag
  val dndVersions by stringSet()
}

internal class JdkUpdaterStartup : StartupActivity.Background {
  override fun runActivity(project: Project) {
    project.service<JdkUpdater>().updateNotifications()
  }
}

@State(name = "jdk-update-state", storages = [Storage(StoragePathMacros.CACHE_FILE)], allowLoadInTests = true)
internal class JdkUpdaterState : SimplePersistentStateComponent<JdkUpdaterStateData>(JdkUpdaterStateData()) {
  private val lock = ReentrantLock()

  override fun loadState(state: JdkUpdaterStateData) = lock.withLock {
    super.loadState(state)
  }

  private fun key(forJdk: Sdk, feedItem: JdkItem) = "for(${forJdk.name})-${feedItem.fullPresentationText}"

  fun isAllowed(forJdk: Sdk, feedItem: JdkItem) = lock.withLock {
    key(forJdk, feedItem) !in state.dndVersions
  }

  fun blockVersion(forJdk: Sdk, feedItem: JdkItem) = lock.withLock {
    state.dndVersions += key(forJdk, feedItem)
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
        .knownSdks //TODO: include AlternativeSdkRootsProvider here too!
        .filter { it.sdkType is JavaSdkType && it.sdkType !is DependentSdkType }

      if (knownSdks.isEmpty()) return@collectSdksPromise

      ProgressManager.getInstance().run(
        object : Task.Backgroundable(project, ProjectBundle.message("progress.title.checking.for.jdk.updates"), true, ALWAYS_BACKGROUND) {
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
        if (!service<JdkUpdaterState>().isAllowed(jdk, feedItem)) return@forEach
        if (VersionComparatorUtil.compare(feedItem.jdkVersion, actualItem.jdkVersion) <= 0) return@forEach

        val title = ProjectBundle.message("notification.title.jdk.update.found")
        val message = ProjectBundle.message("notification.text.jdk.update.found", jdk.name, feedItem.fullPresentationText, actualItem.fullPresentationText)

        NotificationGroupManager.getInstance().getNotificationGroup("Update JDK")
          .createNotification(title, message, NotificationType.INFORMATION, null)
          .setImportant(true)
          .addAction(NotificationAction.createSimple(
            ProjectBundle.message("notification.link.jdk.update.apply"),
            Runnable { updateJdk(project, jdk, feedItem) }))
          .addAction(NotificationAction.createSimple(
            ProjectBundle.message("notification.link.jdk.update.skip"),
            Runnable {
              service<JdkUpdaterState>().blockVersion(jdk, feedItem)
            }))
          .notify(project)
      }
  }

  private fun updateJdk(project: Project, jdk: Sdk, feedItem: JdkItem) {
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, ProjectBundle.message("progress.title.updating.jdk.0.to.1", jdk.name, feedItem.fullPresentationText), true, ALWAYS_BACKGROUND) {

        override fun run(indicator: ProgressIndicator) {
          val installer = JdkInstaller.getInstance()

          val newJdkHome =
            //Optimization: try to check if a given JDK is already installed
            installer.findLocallyInstalledJdk(feedItem) ?: run {
              val prepare = installer.prepareJdkInstallation(feedItem, installer.defaultInstallDir(feedItem))
              installer.installJdk(prepare, indicator, project)
              prepare.javaHome
            }

          runWriteAction {
            jdk.sdkModificator.apply {
              removeAllRoots()
              homePath = newJdkHome.systemIndependentPath
            }.commitChanges()


            val sdkType = jdk.sdkType as? SdkType ?: return@runWriteAction null

            sdkType.setupSdkPaths(jdk)
          }
        }
      }
    )
  }
}
