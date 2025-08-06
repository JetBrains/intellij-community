// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelProcess
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

private val logger = fileLogger()

/**
 * Kills process when scope finishes
 */
@ApiStatus.Internal
fun EelProcess.bindToScope(scope: CoroutineScope) {
  scope.bindProcessToScopeImpl(
    logger = logger,
    processNameForDebug = this.toString(),
    ProcessFunctions(
      waitForExit = { exitCode.await() },
      killProcess = { kill() }
    )
  )
}
