// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.customization

/**
 * A base class for implementations of [ExternalProductResourceUrls] describing IDEs developed by JetBrains.
 */
abstract class BaseJetBrainsExternalProductResourceUrls : ExternalProductResourceUrls {
  override val updatesMetadataXmlUrl: String
    get() = "https://www.jetbrains.com/updates/updates.xml"
  
  abstract override val basePatchDownloadUrl: String
}