// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.customization

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.system.CpuArch
import org.jetbrains.annotations.ApiStatus

/**
 * A base class for implementations of [ExternalProductResourceUrls] describing IDEs developed by JetBrains.
 */
abstract class BaseJetBrainsExternalProductResourceUrls : ExternalProductResourceUrls {
  override val updatesMetadataXmlUrl: String
    get() = "https://www.jetbrains.com/updates/updates.xml"
  
  abstract override val basePatchDownloadUrl: String

  override val bugReportUrl: ((String) -> Url)?
    get() = { description ->
      Urls.newFromEncoded("https://youtrack.jetbrains.com/newissue").addParameters(mapOf(
        "project" to youtrackProjectId,
        "clearDraft" to "true",
        "description" to description
      ))
    }
  
  abstract val youtrackProjectId: String

  override val technicalSupportUrl: ((description: String) -> Url)?
    get() = { _ ->  
      Urls.newFromEncoded("https://intellij-support.jetbrains.com/hc/en-us/requests/new").addParameters(
        mapOf(
          "ticket_form_id" to "$intellijSupportFormId",
          "product" to intellijSupportProductName,
          "build" to ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode(),
          "os" to currentOsNameForIntelliJSupport(),
          "timezone" to System.getProperty("user.timezone")
        ))
    }
  
  abstract val intellijSupportProductName: String
  
  open val intellijSupportFormId: Int
    get() = 66731
}

/**
 * Supported values for https://intellij-support.jetbrains.com
 * * Linux - `linux`
 * * macOS - `mac`
 * * Windows 10 - `win-10`[[-64]]
 * * Windows 8 - `win-8`[[-64]]
 * * Windows 7 or older - `win-7`[[-64]]
 * * Other - `other-os`
 */
@ApiStatus.Internal
fun currentOsNameForIntelliJSupport(): String = when {
  SystemInfo.isWindows -> {
    "win-" +
    when {
      SystemInfo.isWin10OrNewer -> "-10"
      SystemInfo.isWin8OrNewer -> "-8"
      else -> "-7"
    } + if (!CpuArch.is32Bit()) "-64" else ""
  }
  SystemInfo.isLinux -> {
    "linux"
  }
  SystemInfo.isMac -> {
    "mac"
  }
  else -> {
    "other-os"
  }
}


