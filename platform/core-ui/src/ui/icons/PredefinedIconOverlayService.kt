// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon

@Internal
interface PredefinedIconOverlayService {
  companion object {
    fun getInstance(): PredefinedIconOverlayService = ApplicationManager.getApplication().service()

    fun getInstanceOrNull(): PredefinedIconOverlayService? = ApplicationManager.getApplication().serviceOrNull()
  }

  fun createSymlinkIcon(icon: Icon): Icon
}
