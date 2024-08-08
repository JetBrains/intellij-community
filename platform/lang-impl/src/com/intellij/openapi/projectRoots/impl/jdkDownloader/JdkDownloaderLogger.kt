// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.jps.model.java.JdkVersionDetector

internal object JdkDownloaderLogger : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP: EventLogGroup = EventLogGroup("jdk.downloader", 4)

  private val DOWNLOAD: EventId1<Boolean> = GROUP.registerEvent("download", EventFields.Boolean("success"))

  private val DETECTED_SDK: EventId2<String?, Int> = GROUP.registerEvent("detected",
                                                                         EventFields.String("product", JdkVersionDetector.VENDORS),
                                                                         EventFields.Int("version"))

  private val DOWNLOADED_SDK: EventId2<String?, Int> = GROUP.registerEvent("jdk.downloaded",
                                                                         EventFields.String("product", JdkVersionDetector.VENDORS),
                                                                         EventFields.Int("version"))

  private val FAILURE: EventId1<DownloadFailure> = GROUP.registerEvent("failure",
                                                                       EventFields.Enum("reason", DownloadFailure::class.java))

  enum class DownloadFailure {
    WrongProtocol, WSLIssue, FileDoesNotExist, RuntimeException, IncorrectFileSize, ChecksumMismatch, ExtractionFailed, Cancelled,
  }

  fun logDownload(success: Boolean) {
    DOWNLOAD.log(success)
  }

  fun logDownload(success: Boolean, item: JdkItem) {
    DOWNLOAD.log(success)

    if (success) {
      val variant = item.detectVariant()
      DOWNLOADED_SDK.log(variant.displayName, item.jdkMajorVersion)
    }
  }

  fun logFailed(failure: DownloadFailure) {
    FAILURE.log(failure)
  }

  @JvmStatic
  fun logDetected(jdkInfo: JdkVersionDetector.JdkVersionInfo?) {
    val (name, version) = when {
                            jdkInfo == null -> null
                            jdkInfo.variant.displayName in JdkVersionDetector.VENDORS -> jdkInfo.variant.displayName to jdkInfo.version.feature
                            else -> JdkVersionDetector.Variant.Unknown.displayName to jdkInfo.version.feature
                          } ?: return
    DETECTED_SDK.log(name, version)
  }
}