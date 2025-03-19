// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.*
import com.intellij.openapi.roots.ui.configuration.UnknownSdk
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.text.VersionComparatorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Duration.Companion.seconds

/**
 * Used to collect additional [Sdk] instances to check for a possible JDK update.
 */
private val EP_NAME: ExtensionPointName<JdkUpdateCheckContributor> =
  ExtensionPointName("com.intellij.jdkUpdateCheckContributor")

interface JdkUpdateCheckContributor {
  /**
   * Executed from any thread (possibly without read-lock) to collect SDKs, which should be considered for JDK Update check.
   */
  fun contributeJdks(project: Project): List<Sdk>
}

private fun isEnabled(project: Project): Boolean {
  return !project.isDefault &&
         !project.isDisposed &&
         Registry.`is`("jdk.updater") &&
         !ApplicationManager.getApplication().isUnitTestMode &&
         !ApplicationManager.getApplication().isHeadlessEnvironment
}

private class JdkUpdaterStartup : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    delay(60.seconds) // no hurry!

    if (isEnabled(project)) {
      updateNotifications(project)
    }
  }

  suspend fun updateNotifications(project: Project) {
    val knownSdks = suspendCancellableCoroutine<Collection<Sdk>> { continuation ->
      project.service<JdkUpdatesCollectorQueue>().queue(object: UnknownSdkTrackerTask {
        override fun createCollector(): UnknownSdkCollector {
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
          if (!isEnabled(project)) {
            continuation.resumeWith(Result.success(emptyList()))
            return
          }

          // this callback happens in the GUI thread!
          val knownSdks = snapshot.knownSdks
            .filter { it.sdkType is JavaSdkType && it.sdkType !is DependentSdkType }

          continuation.resumeWith(Result.success(knownSdks))
        }
      })
    }

    if (knownSdks.isEmpty()) return

    withBackgroundProgress(project, ProjectBundle.message("progress.title.checking.for.jdk.updates")) {
      coroutineToIndicator {
        updateWithSnapshot(knownSdks.distinct().sortedBy { it.name }, ProgressManager.getInstance().progressIndicator)
      }
    }
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

      var showVendor = false
      val comparison = VersionComparatorUtil.compare(feedItem.jdkVersion, actualItem.jdkVersion)
      if (comparison < 0) continue
      else if (comparison == 0) {
        if (feedItem.jdkVendorVersion == null || actualItem.jdkVendorVersion == null) continue
        if (VersionComparatorUtil.compare(feedItem.jdkVendorVersion, actualItem.jdkVendorVersion) <= 0) continue
        showVendor = true
      }

      notifications.showNotification(jdk, actualItem, feedItem, showVendor)
      noUpdatesFor -= jdk
    }

    // handle the case, when a JDK is no longer requiring an update
    for (jdk in noUpdatesFor) {
      notifications.hideNotification(jdk)
    }
  }
}

@Service(Service.Level.PROJECT)
private class JdkUpdatesCollectorQueue(coroutineScope: CoroutineScope)
  : UnknownSdkCollectorQueue(mergingTimeSpaceMillis = 7_000, coroutineScope = coroutineScope)