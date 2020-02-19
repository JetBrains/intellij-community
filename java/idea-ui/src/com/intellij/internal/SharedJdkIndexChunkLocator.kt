// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.index.SharedIndexExtensions
import com.intellij.internal.SharedIndexesLogger.logDownloadNotifications
import com.intellij.internal.SharedIndexesLogger.logNotification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallRequest
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallerListener
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.indexing.hash.SharedIndexChunkConfiguration
import com.intellij.util.indexing.hash.SharedIndexChunkConfigurationImpl
import com.intellij.util.indexing.provided.SharedIndexChunkLocator
import com.intellij.util.indexing.provided.SharedIndexChunkLocator.ChunkDescriptor
import kotlinx.coroutines.runBlocking
import org.jetbrains.concurrency.await
import java.nio.file.Path

private fun isSharedIndexesDownloadEnabled() = ApplicationManager.getApplication()?.isUnitTestMode == false && Registry.`is`(SharedIndexExtensions.SHARED_INDEXES_DOWNLOAD_KEY)

class SharedJdkIndexChunkPreloader : JdkInstallerListener {
  override fun onJdkDownloadStarted(request: JdkInstallRequest, project: Project?) {
    if (!isSharedIndexesDownloadEnabled()) return

    val jdk = request.item
    val task = object: Task.Backgroundable(project, "Downloading Shared Indexes for ${jdk.fullPresentationText}", true, PerformInBackgroundOption.ALWAYS_BACKGROUND){
      override fun run(indicator: ProgressIndicator) {
        prefetchSharedIndex(jdk, indicator, project)
      }
    }

    ProgressManager.getInstance().run(task)
  }

  private fun prefetchSharedIndex(jdk: JdkItem,
                                  indicator: ProgressIndicator,
                                  project: Project?) {
    val request = SharedIndexRequest(kind = "jdk", aliases = jdk.sharedIndexAliases)
    val info = SharedIndexesLoader.getInstance().lookupSharedIndex(request, indicator)

    logNotification(project,"Shared Index entry for downloading ${jdk.fullPresentationText} is found with $info\n${info?.url}")
    if (info == null) return

    val descriptor = logDownloadNotifications(project, jdk.fullPresentationText, info.toChunkDescriptor(emptySet()))
    val service = ApplicationManager.getApplication()
      .getService(SharedIndexChunkConfiguration::class.java) as SharedIndexChunkConfigurationImpl

    runBlocking {
      service.preloadIndex(descriptor, indicator).await()
    }
  }
}

class SharedJdkIndexChunkLocator: SharedIndexChunkLocator {

  override fun locateIndex(project: Project,
                           entries: MutableCollection<out OrderEntry>,
                           indicator: ProgressIndicator): List<ChunkDescriptor> {
    if (!isSharedIndexesDownloadEnabled()) return emptyList()

    //TODO: it should cache the known objects to return them offline first
    //TODO: what if I have a fresh index update for an already downloaded chunk?

    val jdkToEntries = entries.filterIsInstance<JdkOrderEntry>()
      .mapNotNull { it.jdk?.to(it) }
      .filter { it.first.sdkType is JavaSdkImpl }
      .groupBy({ it.first }, {it.second})
      .toMap()

    if (jdkToEntries.isEmpty()) return emptyList()
    val type = JavaSdk.getInstance() as JavaSdkImpl

    indicator.text = "Resolving shared indexes..."

    val result = mutableListOf<ChunkDescriptor>()
    jdkToEntries.entries.forEachWithProgress(indicator) { (sdk, sdkEntries) ->
      val sdkHash = type.computeJdkFingerprint(sdk)
      logNotification(project, "Hash for JDK \"${sdk.name}\" is $sdkHash")
      if (sdkHash == null) {
        return@forEachWithProgress
      }

      val aliases = sequence {
        yield(sdk.name)

        val javaVersion = runCatching { type.getJavaVersion(sdk) }.getOrNull()
        if (javaVersion != null) {
          yield(javaVersion.toFeatureMinorUpdateString())
          yield(javaVersion.toFeatureString())
        }
      }.distinct().toList()

      val request = SharedIndexRequest(kind = "jdk", hash = sdkHash, aliases = aliases)
      val info = SharedIndexesLoader.getInstance().lookupSharedIndex(request, indicator)
      logNotification(project, "Shared Index entry for JDK \"${sdk.name}\" is found with $info\n${info?.url}")
      if (info == null) {
        return@forEachWithProgress
      }

      result += logDownloadNotifications(project, "JDK \"${sdk.name}\" with $info", info.toChunkDescriptor(sdkEntries))
    }

    return result
  }
}

private object SharedIndexesLogger {
  private val LOG = logger<SharedJdkIndexChunkLocator>()

  val notificationGroup by lazy {
    NotificationGroup.logOnlyGroup("SharedIndexes")
  }

  fun logDownloadNotifications(project: Project?, name: String, descriptor: ChunkDescriptor) = object: ChunkDescriptor by descriptor {
    override fun downloadChunk(targetFile: Path, indicator: ProgressIndicator) {
      logNotification(project, "Downloading Shared Index for $name")
      try {
        return descriptor.downloadChunk(targetFile, indicator)
      } finally {
        logNotification(project, "Completed Downloading Shared Index for $name")
      }
    }
  }

  fun logNotification(project: Project?, message: String) {
    LOG.warn("SharedIndexes: $message")
    if (ApplicationManager.getApplication().isInternal || Registry.`is`("shared.indexes.eventLogMessages")) {
      val msg = notificationGroup.createNotification(message, NotificationType.INFORMATION)
      Notifications.Bus.notify(msg, project)
    }
  }
}


inline fun <T> Collection<T>.forEachWithProgress(indicator: ProgressIndicator, action: (T) -> Unit) {
  indicator.pushState()
  try {
    indicator.isIndeterminate = false
    indicator.fraction = 0.0

    val count = this.size
    this.forEachIndexed { index, t ->
      indicator.checkCanceled()
      action(t)
      indicator.fraction = index.toDouble() / count
    }
  } finally {
    indicator.popState()
  }
}
