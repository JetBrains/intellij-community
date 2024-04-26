// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import org.jetbrains.annotations.ApiStatus
import java.awt.Image

@ApiStatus.Internal
interface BannerImageProvider {

  companion object {
    @JvmStatic
    fun getInstance(): BannerImageProvider? = ApplicationManager.getApplication().serviceOrNull()
  }

  fun getIDEBanner(): Image?

}