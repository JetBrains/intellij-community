// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import java.awt.Image

interface BannerImageProvider {

  companion object {
    @JvmStatic
    fun getInstance(): BannerImageProvider = ApplicationManager.getApplication().service()
  }

  fun getIDEBanner(): Image?

}