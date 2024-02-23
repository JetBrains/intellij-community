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
  private val GROUP: EventLogGroup = EventLogGroup("npw.jdk.combo", 1)
  private const val UNKNOWN_VENDOR = "unknown"
  private val KNOWN_VENDORS = JdkVersionDetector.Variant.entries
    .mapNotNull { it.displayName }
    .toList() + UNKNOWN_VENDOR

  private val SETUP_EXISTING: EventId2<String, Int> = GROUP.registerEvent("setup.existing",
                                                                          EventFields.String("vendor", KNOWN_VENDORS),
                                                                          EventFields.Int("version"))
  private val SETUP_NO_JDK: EventId = GROUP.registerEvent("no.jdk")
  private val DOWNLOAD_JDK: EventId2<String, Int> = GROUP.registerEvent("download.jdk",
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

    SETUP_EXISTING.log(variant, version)
  }

  fun noJdkRegistered() {
    SETUP_NO_JDK.log()
  }

  private fun findSdkVersion(sdkVersionString: String?): Int {
    val versionRegex = Regex("([0-9]+)(?:[.0-9]+)?")
    val version = when (sdkVersionString) {
      null -> -1
      else -> versionRegex.find(sdkVersionString)?.groups?.get(1)?.value?.toInt() ?: -1
    }
    return version
  }

  fun jdkDownloaded(item: JdkItem) {
    val vendor = when (item.product.vendor) {
      in KNOWN_VENDORS -> item.product.vendor
      else -> UNKNOWN_VENDOR
    }

    DOWNLOAD_JDK.log(vendor, findSdkVersion(item.presentableMajorVersionString))
  }
}