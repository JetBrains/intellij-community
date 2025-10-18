// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Image

@ApiStatus.Internal
interface BannerImageProvider {

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<BannerImageProvider> = ExtensionPointName.create("com.intellij.commercialBannerImageProvider")

    @JvmStatic
    fun getInstance(): BannerImageProvider? = EP_NAME.extensionList.firstOrNull()
  }

  fun getIDEBanner(size: Dimension): Image?
}