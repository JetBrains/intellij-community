// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private val logger = fileLogger()

@ApiStatus.Internal
fun EelProcess.bindToScope(scope: CoroutineScope) {
  val context = CoroutineName("Waiting for process $this") + Dispatchers.IO
  if (!scope.isActive) {
    logger.warn("Scope $scope is dead, killing process $this")
    scope.launch(context, start = CoroutineStart.UNDISPATCHED) {
      killAndJoin()
    }
  }

  scope.launch(context, start = CoroutineStart.UNDISPATCHED) {
    try {
      exitCode.await()
    }
    catch (e: CancellationException) {
      killAndJoin()
      throw e
    }
  }
}

private suspend fun EelProcess.killAndJoin() {
  withContext(NonCancellable) {
    logger.warn("Sending kill to $this")
    kill()
    logger.warn("Waiting $this to exit")
    exitCode.await()
    logger.warn("Process $this died")
  }
}