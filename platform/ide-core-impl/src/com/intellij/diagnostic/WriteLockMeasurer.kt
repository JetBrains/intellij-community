// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface WriteLockMeasurer {
  companion object {
    @JvmStatic
    fun getInstance(): WriteLockMeasurer {
      return ApplicationManager.getApplication().service<WriteLockMeasurer>()
    }
  }
}