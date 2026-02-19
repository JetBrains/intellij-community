// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.window.to.foreground

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface BringProcessWindowToForegroundSupport {
  companion object {
    fun getInstance(): BringProcessWindowToForegroundSupport = ApplicationManager.getApplication().service<BringProcessWindowToForegroundSupport>()
  }

  fun bring(pid: UInt) : Boolean
}

@ApiStatus.Internal
abstract class BringProcessWindowToForegroundSupportApplicable : BringProcessWindowToForegroundSupport {
  abstract fun isApplicable() : Boolean
}

