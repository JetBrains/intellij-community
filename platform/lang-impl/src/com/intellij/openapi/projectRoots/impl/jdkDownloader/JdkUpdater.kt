// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.ProjectTopics
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.projectRoots.impl.*
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.UnknownSdk
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.text.VersionComparatorUtil
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
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

private fun isEnabled(project: Project) = !project.isDefault &&
                                          Registry.`is`("jdk.updater") &&
                                          !ApplicationManager.getApplication().isUnitTestMode &&
                                          !ApplicationManager.getApplication().isHeadlessEnvironment


internal class JdkUpdaterStartup : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (!isEnabled(project)) return
    project.service<JdkUpdatesCollector>().updateNotifications()
  }
}

private val LOG = logger<JdkUpdatesCollector>()

@Service // project
private class JdkUpdatesCollectorQueue : UnknownSdkCollectorQueue(7_000)

@Service
internal class JdkUpdatesCollector(
  private val project: Project
) : Disposable {
  override fun dispose() = Unit

  init {
    schedule()
  }

  private fun isEnabled() = isEnabled(project)


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

    val myLastKnownModificationId = AtomicLong(-100_500)

    project.messageBus
      .connect(this)
      .subscribe(
        ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
          if (event.isCausedByFileTypesChange) return

          //an optimization - we do not scan for JDKs if there we no ProjectRootManager modifications change
          //this avoids VirtualFilePointers invalidation
          val newCounterValue = ProjectRootManager.getInstance(project).modificationCount
          if (myLastKnownModificationId.getAndSet(newCounterValue) == newCounterValue) return

          updateNotifications()
        }
      })
  }

  fun updateNotifications() {
    if (!isEnabled()) return

    project.service<JdkUpdatesCollectorQueue>().queue(object: UnknownSdkTrackerTask {
      override fun createCollector(): UnknownSdkCollector? {
        if (!isEnabled()) return null
        return object : UnknownSdkCollector(project) {
          override fun getContributors(): List<UnknownSdkContributor> {
            return super.getContributors() + EP_NAME.extensionList.map {
              object : UnknownSdkContributor {
                override fun contributeUnknownSdks(project: Project) = listOf<UnknownSdk>()
                override fun contributeKnownSdks(project: Project): List<Sdk> = it.contributeJdks(project)
              }
            }
          }
        }
      }

      override fun onLookupCompleted(snapshot: UnknownSdkSnapshot) {
        if (!isEnabled()) return

        //this callback happens in the GUI thread!
        val knownSdks = snapshot
          .knownSdks
          .filter { it.sdkType is JavaSdkType && it.sdkType !is DependentSdkType }

        if (knownSdks.isEmpty()) return

        ProgressManager.getInstance().run(
          object : Task.Backgroundable(project, ProjectBundle.message("progress.title.checking.for.jdk.updates"), true, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
              updateWithSnapshot(knownSdks.distinct().sortedBy { it.name }, indicator)
            }
          }
        )
      }
    })
  }

  private fun updateWithSnapshot(knownSdks: List<Sdk>, indicator: ProgressIndicator) {
    val jdkFeed by lazy {
      val listDownloader = JdkListDownloader.getInstance()

      var items = listDownloader.downloadModelForJdkInstaller(predicate = JdkPredicate.default(), progress = indicator)

      if (SystemInfo.isWindows && WslDistributionManager.getInstance().installedDistributions.isNotEmpty()) {
        @Suppress("SuspiciousCollectionReassignment")
        items += listDownloader.downloadModelForJdkInstaller(predicate = JdkPredicate.forWSL(), progress = indicator)
      }
      items.toList()
    }

    val notifications = service<JdkUpdaterNotifications>()

    val noUpdatesFor = HashSet<Sdk>(knownSdks)
    for (jdk in knownSdks) {
      val actualItem = JdkInstaller.getInstance().findJdkItemForInstalledJdk(jdk.homePath) ?: continue
      val feedItem = jdkFeed.firstOrNull {
        it.suggestedSdkName == actualItem.suggestedSdkName && it.arch == actualItem.arch && it.os == actualItem.os
      } ?: continue

      if (!service<JdkUpdaterState>().isAllowed(jdk, feedItem)) continue

      //internal versions are not considered here (JBRs?)
      if (VersionComparatorUtil.compare(feedItem.jdkVersion, actualItem.jdkVersion) <= 0) continue

      notifications.showNotification(jdk, actualItem, feedItem)
      noUpdatesFor -= jdk
    }

    //handle the case, when a JDK is no longer requires an update
    for (jdk in noUpdatesFor) {
      notifications.hideNotification(jdk)
    }
  }
}

@Service //Application service
class JdkUpdaterNotifications : Disposable {
  private val lock = ReentrantLock()
  private val pendingNotifications = HashMap<Sdk, JdkUpdateNotification>()

  override fun dispose() : Unit = lock.withLock {
    pendingNotifications.clear()
  }

  fun showNotification(jdk: Sdk, actualItem: JdkItem, newItem: JdkItem) : Unit = lock.withLock {
    val newNotification = JdkUpdateNotification(
      jdk = jdk,
      oldItem = actualItem,
      newItem = newItem,
      whenComplete = {
        lock.withLock {
          pendingNotifications.remove(jdk, it)
        }
      }
    )

    val currentNotification = pendingNotifications[jdk]
    if (currentNotification != null && !currentNotification.tryReplaceWithNewerNotification(newNotification)) return
    pendingNotifications[jdk] = newNotification
    newNotification
  }.showNotificationIfAbsent()

  fun hideNotification(jdk: Sdk) = lock.withLock {
    pendingNotifications[jdk]?.tryReplaceWithNewerNotification()
  }
}
