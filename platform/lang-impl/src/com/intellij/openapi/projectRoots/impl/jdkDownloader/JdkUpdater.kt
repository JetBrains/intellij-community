// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
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
import com.intellij.openapi.projectRoots.impl.UnknownSdkContributor
import com.intellij.openapi.roots.ui.configuration.UnknownSdk
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.xmlb.annotations.OptionTag
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This extension point is used to collect
 * additional [Sdk] instances to check for a possible
 * JDK update
 */
private val EP_NAME = ExtensionPointName.create<JdkUpdateCheckContributor>("com.intellij.jdkUpdateCheckContributor")

interface JdkUpdateCheckContributor {
  /**
   * Executed from any thread (possibly without read-lock) to
   * collect SDKs, which should be considered for
   * JDK Update check
   */
  fun contributeJdks(project: Project): List<Sdk>
}

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

private val LOG = logger<JdkUpdater>()

private fun isEnabled() = Registry.`is`("jdk.updater") &&
                          !ApplicationManager.getApplication().isUnitTestMode &&
                          !ApplicationManager.getApplication().isHeadlessEnvironment

@Service
internal class JdkUpdater(
  private val project: Project
) : Disposable {
  override fun dispose() = Unit

  init {
    schedule()
  }

  private fun schedule() {
    if (!isEnabled()) return

    val future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
      Runnable {
        if (project.isDisposed) return@Runnable

        try {
          updateNotifications()
        }
        catch (t: Throwable) {
          if (t is ControlFlowException) return@Runnable
          LOG.warn("Failed to complete JDK Update check. ${t.message}", t)
        }
      },
      12,
      12,
      TimeUnit.HOURS
    )
    Disposer.register(this, Disposable { future.cancel(false) })
  }

  fun updateNotifications() {
    if (!isEnabled()) return

    object : UnknownSdkCollector(project) {
      override fun getContributors(): List<UnknownSdkContributor> {
        return super.getContributors() + EP_NAME.extensionList.map {
          object : UnknownSdkContributor {
            override fun contributeUnknownSdks(project: Project) = listOf<UnknownSdk>()
            override fun contributeKnownSdks(project: Project): List<Sdk> = it.contributeJdks(project)
          }
        }
      }
    }.collectSdksPromise { snapshot ->
      //this callback happens in the GUI thread!
      val knownSdks = snapshot
        .knownSdks
        .filter { it.sdkType is JavaSdkType && it.sdkType !is DependentSdkType }
        .distinct()
        .sortedBy { it.name }

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
    val jdkFeed by lazy {
      JdkListDownloader
        .getInstance()
        .downloadForUI(progress = indicator)
        .associateBy { it.suggestedSdkName }
    }

    for (jdk in knownSdks) {
      val actualItem = JdkInstaller.getInstance().findJdkItemForInstalledJdk(jdk.homePath) ?: continue
      val feedItem = jdkFeed[actualItem.suggestedSdkName] ?: continue

      if (!service<JdkUpdaterState>().isAllowed(jdk, feedItem)) continue
      if (VersionComparatorUtil.compare(feedItem.jdkVersion, actualItem.jdkVersion) <= 0) continue

      val title = ProjectBundle.message("notification.title.jdk.update.found")
      val message = ProjectBundle.message("notification.text.jdk.update.found",
                                          jdk.name,
                                          feedItem.fullPresentationText,
                                          actualItem.fullPresentationText)

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
    val title = ProjectBundle.message("progress.title.updating.jdk.0.to.1", jdk.name, feedItem.fullPresentationText)
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, title, true, ALWAYS_BACKGROUND) {

        override fun run(indicator: ProgressIndicator) {
          val installer = JdkInstaller.getInstance()

          val newJdkHome = try {
            val newJdkHome =
              //Optimization: try to check if a given JDK is already installed
              installer.findLocallyInstalledJdk(feedItem) ?: run {
                val prepare = installer.prepareJdkInstallation(feedItem, installer.defaultInstallDir(feedItem))
                installer.installJdk(prepare, indicator, project)
                prepare.javaHome
              }

            indicator.text = ProjectBundle.message("progress.text.updating.jdk.setting.up")

            //make sure VFS sees the files and sets up the JDK correctly
            VfsUtil.markDirtyAndRefresh(false, true, true, newJdkHome.toFile())

            newJdkHome
          }
          catch (t: Throwable) {
            if (t is ControlFlowException) throw t

            LOG.warn("Failed to update $jdk to $feedItem. ${t.message}", t)
            showUpdateErrorNotification(feedItem)
            return
          }

          invokeLater {
            runWriteAction {
              try {
                jdk.sdkModificator.apply {
                  removeAllRoots()
                  homePath = newJdkHome.systemIndependentPath
                  versionString = feedItem.versionString
                }.commitChanges()

                (jdk.sdkType as? SdkType)?.setupSdkPaths(jdk)
              }
              catch (t: Throwable) {
                if (t is ControlFlowException) throw t
                LOG.warn("Failed to apply downloaded JDK update for $jdk from $feedItem at $newJdkHome. ${t.message}", t)
                showUpdateErrorNotification(feedItem)
              }
            }
          }
        }

        private fun showUpdateErrorNotification(feedItem: JdkItem) {
          val message = ProjectBundle.message("progress.title.updating.jdk.failed", feedItem.fullPresentationText)
          NotificationGroupManager.getInstance().getNotificationGroup("JDK Update")
            .createNotification(type = NotificationType.ERROR)
            .setTitle(title)
            .setContent(message)
            .addAction(NotificationAction.createSimple(
              ProjectBundle.message("notification.link.jdk.update.retry"),
              Runnable { updateJdk(project, jdk, feedItem) }))
            .addAction(NotificationAction.createSimple(
              ProjectBundle.message("notification.link.jdk.update.skip"),
              Runnable {
                service<JdkUpdaterState>().blockVersion(jdk, feedItem)
              }))
            .notify(project)
        }
      }
    )
  }
}
