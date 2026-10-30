// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.debugger.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun <T> runBlockingAssertNotInReadAction(block: suspend CoroutineScope.() -> T): T {
  if (ApplicationManager.getApplication().isInternal
      && ApplicationManager.getApplication().isReadAccessAllowed
      && !ProgressManager.getInstance().hasProgressIndicator()) {
    fileLogger().error("Call runBlocking from read action without indicator")
  }
  return runBlockingMaybeCancellable(block)
}
