// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.ex

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface LowLevelProjectOpenProcessor {

  suspend fun shouldOpenInNewProcess(projectPath: Path): Boolean {
    return false
  }

  suspend fun beforeProjectOpened(projectPath: Path): PrepareProjectResult {
    return PrepareProjectResult.CONTINUE
  }

  enum class PrepareProjectResult {
    CONTINUE, CANCEL
  }

  companion object {
    val EP_NAME = ExtensionPointName<LowLevelProjectOpenProcessor>("com.intellij.lowLevelProjectOpenProcessor")
  }
}