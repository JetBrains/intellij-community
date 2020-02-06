// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.indexing.provided.SharedIndexChunkLocator
import com.intellij.util.indexing.provided.SharedIndexChunkLocator.ChunkDescriptor
import java.nio.file.Path

class SharedJdkIndexChunkLocator: SharedIndexChunkLocator {
  private val LOG = logger<SharedJdkIndexChunkLocator>()

  override fun locateIndex(project: Project,
                           entries: MutableCollection<out OrderEntry>,
                           indicator: ProgressIndicator): List<ChunkDescriptor> {
    if (ApplicationManager.getApplication().isUnitTestMode) return emptyList()
    if (!Registry.`is`("shared.indexes.download")) return emptyList()

    //TODO: it should cache the known objects to return them offline first
    //TODO: what if I have a fresh index update for an already downloaded chunk?

    val jdkToEntries = entries.filterIsInstance<JdkOrderEntry>()
      .mapNotNull { it.jdk?.to(it) }
      .filter { it.first.sdkType is JavaSdkImpl }
      .groupBy({ it.first }, {it.second})
      .toMap()

    if (jdkToEntries.isEmpty()) return emptyList()
    val type = JavaSdk.getInstance() as JavaSdkImpl

    val sharedIndexType = "jdk"
    return jdkToEntries.mapNotNull { (sdk, sdkEntries) ->
      val sdkHash = type.computeJdkFingerprint(sdk)
      logNotification(project, "Hash for JDK \"${sdk.name}\" is $sdkHash")

      sdkHash ?: return@mapNotNull null

      val info = SharedIndexesLoader.getInstance().lookupSharedIndex(SharedIndexRequest(kind = sharedIndexType, hash = sdkHash), indicator)
      logNotification(project, "Shared Index entry for JDK \"${sdk.name}\" is found with $info\n${info?.url}")

      info ?: return@mapNotNull null

      object: ChunkDescriptor {
        override fun getChunkUniqueId() = "jdk-$sdkHash-${info.version.weakVersionHash}"
        override fun getSupportedInfrastructureVersion() = info.version

        override fun getOrderEntries() = sdkEntries

        override fun downloadChunk(targetFile: Path, indicator: ProgressIndicator) {
          logNotification(project, "Downloading Shared Index for JDK \"${sdk.name}\" with $info...")
          try {
            SharedIndexesLoader.getInstance().downloadSharedIndex(info, indicator, targetFile.toFile())
          } finally {
            logNotification(project, "Completed Downloading Shared Index for JDK \"${sdk.name}\" with $info")
          }
        }
      }
    }
  }

  private val notificationGroup by lazy {
    NotificationGroup.logOnlyGroup("SharedIndexes")
  }

  private fun logNotification(project: Project, message: String) {
    LOG.warn("SharedIndexes: $message")
    if (ApplicationManager.getApplication().isInternal || Registry.`is`("shared.indexes.eventLogMessages")) {
      val msg = notificationGroup.createNotification(message, NotificationType.INFORMATION)
      Notifications.Bus.notify(msg, project)
    }
  }
}
