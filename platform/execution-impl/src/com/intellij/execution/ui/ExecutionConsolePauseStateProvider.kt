// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ExecutionConsolePauseStateProvider {
  fun isPaused(project: Project, console: ExecutionConsole): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ExecutionConsolePauseStateProvider> =
      ExtensionPointName.create("com.intellij.execution.consolePauseStateProvider")
  }
}
