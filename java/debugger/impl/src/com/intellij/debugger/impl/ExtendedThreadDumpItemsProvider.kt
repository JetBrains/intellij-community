// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.unscramble.DumpItem
import java.util.concurrent.CompletableFuture
import com.intellij.debugger.engine.SuspendContextImpl
import org.jetbrains.annotations.ApiStatus

/**
 * A provider of extra [DumpItem] instances, which may be shown in [com.intellij.unscramble.ThreadDumpPanel].
 * This provider may provide coroutines or virtual threads as DumpItems to merge them with the regular Java thread dump.
 *
 * In the Debug evaluation is possible if suspendContext is available:
 *  1. If the execution is stopped on a breakpoint, then suspendContext is available
 *  2. If the execution was running, then suspendContext may be obtained by suspending some thread on a breakpoint:
 *       * This provider first tries to use Intellij Suspend Helper provided by the debugger-agent.
 *       If available, the Helper thread is stopped on the method entry to evaluate the dump.
 *       * If the Helper thread is not available, then the provider first tries to find a user thread,
 *       with the top frame in the non-native code, if found, then a breakpoint is set on its last location.
 *       The thread is resumed and is expected to immediately hit the breakpoint
 *       and evaluate the dump.
 *
 *  The resulting dump is returned as a [CompletableFuture] and awaited at the call-site.
 */
@ApiStatus.Internal
abstract class ExtendedThreadDumpItemsProvider {
  companion object {
    val EP: ExtensionPointName<ExtendedThreadDumpItemsProvider> = ExtensionPointName.Companion.create("com.intellij.debugger.dumpItemsProvider")
  }

  open val isEnabled: Boolean
    get() = true

  /** Computes a list of additional dump items using the given suspend context which is ready for evaluation. */
  abstract fun compute(suspendContext: SuspendContextImpl): List<DumpItem>
}