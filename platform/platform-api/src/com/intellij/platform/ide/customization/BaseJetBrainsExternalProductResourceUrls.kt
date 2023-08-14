// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.customization

import com.intellij.util.Url
import com.intellij.util.Urls

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
}