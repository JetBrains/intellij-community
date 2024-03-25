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
  private val GROUP: EventLogGroup = EventLogGroup("npw.jdk.combo", 2)
  private const val UNKNOWN_VENDOR = "unknown"
  @OptIn(ExperimentalStdlibApi::class)
  private val KNOWN_VENDORS = JdkVersionDetector.Variant.entries
    .mapNotNull { it.displayName }
    .toList() + UNKNOWN_VENDOR

  private val JDK_REGISTERED: EventId2<String, Int> = GROUP.registerEvent("jdk.registered",
                                                                          EventFields.String("vendor", KNOWN_VENDORS),
                                                                          EventFields.Int("version"))
  private val NO_JDK_SELECTED: EventId = GROUP.registerEvent("no.jdk.selected")
  private val JDK_DOWNLOADED: EventId2<String, Int> = GROUP.registerEvent("jdk.downloaded",
                                                                          EventFields.String("vendor", KNOWN_VENDORS),
                                                                          EventFields.Int("version"))

  override fun getGroup(): EventLogGroup = GROUP

  fun jdkRegistered(sdk: Sdk) {
    val sdkVersionString = sdk.versionString

    val variant = when (sdkVersionString) {
      null -> UNKNOWN_VENDOR
      else -> KNOWN_VENDORS.firstOrNull { sdkVersionString.contains(it) } ?: UNKNOWN_VENDOR
    }

    val version = findSdkVersion(sdkVersionString)

    JDK_REGISTERED.log(variant, version)
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

  fun jdkDownloaded(item: JdkItem) {
    val vendor = when (item.product.vendor) {
      in KNOWN_VENDORS -> item.product.vendor
      else -> UNKNOWN_VENDOR
    }

    JDK_DOWNLOADED.log(vendor, findSdkVersion(item.presentableMajorVersionString))
  }
}