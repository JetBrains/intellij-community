// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.sun.jdi.Location
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PositionManagerAsync : PositionManager {
  suspend fun getSourcePositionAsync(location: Location?): SourcePosition?

  @RequiresBlockingContext
  override fun getSourcePosition(location: Location?): SourcePosition? {
    if (ApplicationManager.getApplication().isInternal
        && ApplicationManager.getApplication().isReadAccessAllowed
        && !ProgressManager.getInstance().hasProgressIndicator()) {
      fileLogger().error("Call runBlocking from read action without indicator")
    }
    return runBlockingMaybeCancellable {
      getSourcePositionAsync(location)
    }
  }
}
