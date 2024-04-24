// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

/**
 * This service decides if IJent should be used for doing some tasks on WSL.
 */
@ApiStatus.Internal
interface WslIjentAvailabilityService {
  /** `true` if IDE should run processes on WSL via IJent, `false` for direct invocations of `wsl.exe` for every process. */
  fun runWslCommandsViaIjent(): Boolean

  companion object {
    @JvmStatic
    fun getInstance(): WslIjentAvailabilityService = ApplicationManager.getApplication().service()
  }
}