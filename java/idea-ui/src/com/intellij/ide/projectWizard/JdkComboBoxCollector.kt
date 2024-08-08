// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import org.jetbrains.jps.model.java.JdkVersionDetector

internal object JdkComboBoxCollector: CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("npw.jdk.combo", 4)

  private val JDK_REGISTERED: EventId2<String, Int> = GROUP.registerEvent("jdk.registered",
                                                                          EventFields.String("vendor", JdkVersionDetector.VENDORS),
                                                                          EventFields.Int("version"))
  private val NO_JDK_SELECTED: EventId = GROUP.registerEvent("no.jdk.selected")
  private val JDK_DOWNLOADED: EventId2<String, Int> = GROUP.registerEvent("jdk.downloaded",
                                                                          EventFields.String("vendor", JdkVersionDetector.VENDORS),
                                                                          EventFields.Int("version"))

  override fun getGroup(): EventLogGroup = GROUP

  fun jdkRegistered(sdk: Sdk) {
    val sdkVersionString = sdk.versionString

    val variant = when (sdkVersionString) {
      null -> JdkVersionDetector.Variant.Unknown.displayName
      else -> JdkVersionDetector.VENDORS.firstOrNull { sdkVersionString.contains(it) } ?: JdkVersionDetector.Variant.Unknown.displayName
    }

    val version = findSdkVersion(sdkVersionString)

    JDK_REGISTERED.log(variant, version)
  }

  fun jdkDownloaded(item: JdkItem) {
    val variant = item.detectVariant()
    JDK_DOWNLOADED.log(variant.displayName, findSdkVersion(item.presentableMajorVersionString))
  }

  fun noJdkSelected() {
    NO_JDK_SELECTED.log()
  }

  private fun findSdkVersion(sdkVersionString: String?): Int = when (sdkVersionString) {
    null -> -1
    else -> {
      val matchResult = when {
        "GraalVM" in sdkVersionString -> Regex("Java ([0-9]+)").find(sdkVersionString)
        else -> Regex("([0-9]+)").find(sdkVersionString)
      }
      matchResult?.groups?.get(1)?.value?.toInt() ?: -1
    }
  }
}