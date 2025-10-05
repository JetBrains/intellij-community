// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.impl.runBlockingAssertNotInReadAction
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.sun.jdi.Location
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PositionManagerAsync : PositionManager {
  suspend fun getSourcePositionAsync(location: Location?): SourcePosition?

  @RequiresBlockingContext
  override fun getSourcePosition(location: Location?): SourcePosition? {
    return runBlockingAssertNotInReadAction {
      getSourcePositionAsync(location)
    }
  }
}
