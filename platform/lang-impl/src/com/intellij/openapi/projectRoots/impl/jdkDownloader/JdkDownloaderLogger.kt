// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.jps.model.java.JdkVersionDetector

object JdkDownloaderLogger : CounterUsagesCollector() {

  private const val UNKNOWN_JDK = "Unknown"
  private val JDKS = listOf(
    "AdoptOpenJDK (HotSpot)",
    "AdoptOpenJDK (OpenJ9)",
    "Eclipse Temurin",
    "IBM Semeru",
    "Amazon Corretto",
    "GraalVM",
    "IBM JDK",
    "JetBrains Runtime",
    "BellSoft Liberica",
    "Oracle OpenJDK",
    "SAP SapMachine",
    "Azul Zulu",
    UNKNOWN_JDK
  )

  private val GROUP: EventLogGroup = EventLogGroup("jdk.downloader", 1)

  private val DOWNLOAD: EventId1<Boolean> = GROUP.registerEvent("download", EventFields.Boolean("success"))

  private val DETECTED_SDK: EventId2<String?, Int> = GROUP.registerEvent("detected",
                                                                         EventFields.String("product", JDKS),
                                                                         EventFields.Int("version"))
  private val SELECTED_SDK: EventId2<String?, Int> = GROUP.registerEvent("selected",
                                                                         EventFields.String("product", JDKS),
                                                                         EventFields.Int("version"))

  override fun getGroup(): EventLogGroup = GROUP

  fun logDownload(success: Boolean) {
    DOWNLOAD.log(success)
  }

  fun logSelected(jdkItem: JdkItem) {
    val name = when (jdkItem.product.vendor) {
      "Azul" -> "Azul Zulu"
      "Amazon" -> "Amazon Corretto"
      "JetBrains" -> "JetBrains Runtime"
      "BellSoft" -> "BellSoft Liberica"
      "Oracle" -> "Oracle OpenJDK"
      "SAP" -> "SAP SapMachine"
      "IBM" -> "IBM Semeru"
      "Eclipse" -> "Eclipse Temurin"
      else -> UNKNOWN_JDK
    }
    SELECTED_SDK.log(name, jdkItem.jdkMajorVersion)
  }

  fun logDetected(jdkInfo: JdkVersionDetector.JdkVersionInfo?) {
    val (name, version) = when {
                            jdkInfo == null -> null
                            jdkInfo.variant.displayName in JDKS -> (jdkInfo.variant.displayName ?: UNKNOWN_JDK) to jdkInfo.version.feature
                            else -> UNKNOWN_JDK to jdkInfo.version.feature
                          } ?: return
    DETECTED_SDK.log(name, version)
  }

}