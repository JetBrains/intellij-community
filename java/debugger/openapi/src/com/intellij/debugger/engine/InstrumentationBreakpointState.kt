// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.requests.RequestManager
import com.intellij.debugger.requests.Requestor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InstrumentationBreakpointState {
  val instrumentationId: Int

  val isInstrumentationModeEnabled: Boolean

  fun updateInstrumentationModeEnabled(requestManager: RequestManager, newEnableState: Boolean)
}

@ApiStatus.Internal
interface InstrumentedTechnicalBreakpoint: Requestor
