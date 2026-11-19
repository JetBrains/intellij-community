// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.requests.RequestManager
import com.intellij.debugger.requests.Requestor
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

/**
 * `true` on a [DebugProcess] that must not instrument breakpoints: its breakpoints use regular requests. For a process
 * that adopted a VM from another process: the VM still holds the fragment classes of that process.
 */
@ApiStatus.Internal
@JvmField
val BREAKPOINT_INSTRUMENTATION_OFF: Key<Boolean> = Key.create("BREAKPOINT_INSTRUMENTATION_OFF")

@ApiStatus.Internal
interface InstrumentationBreakpointState {
  val instrumentationId: Int

  val isInstrumentationModeEnabled: Boolean

  fun updateInstrumentationModeEnabled(requestManager: RequestManager, newEnableState: Boolean)
}

@ApiStatus.Internal
interface InstrumentedTechnicalBreakpoint: Requestor

@ApiStatus.Internal
interface InstrumentedTechnicalBreakpointHit: InstrumentedTechnicalBreakpoint {
  val originalRequestor: Requestor

  /** The state of the one fragment this hit belongs to: a breakpoint has one fragment per class loader. */
  val instrumentationState: InstrumentationBreakpointState
}
