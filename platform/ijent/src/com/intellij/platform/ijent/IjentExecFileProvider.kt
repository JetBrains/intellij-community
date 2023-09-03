// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.components.serviceAsync
import java.nio.file.Path

interface IjentExecFileProvider {
  companion object {
    suspend fun instance(): IjentExecFileProvider = serviceAsync()
  }

  enum class SupportedPlatform {
    X86_64__LINUX,
    X86_64__WINDOWS,
  }

  suspend fun getIjentBinary(targetPlatform: SupportedPlatform): Path
}