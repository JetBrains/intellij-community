// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture

suspend fun StringReference.valueAsync(): String = DebuggerUtilsAsync.getStringValueJdiFuture(this).awaitNoDMT()
suspend fun ObjectReference.isCollectedAsync(): Boolean = DebuggerUtilsAsync.isCollectedJdiFuture(this).awaitNoDMT()

/**
 * We can [await] [CompletableFuture] in DMT only if it is not completed in DMT.
 *
 * In that case a deadlock may appear as we wait on DMT of a task that needs DMT to complete.
 *
 * To prevent such situations, [CompletableFuture]s awaited in this method should not be rescheduled via
 * [com.intellij.debugger.impl.DebuggerUtilsAsync.reschedule].
 */
private suspend fun <T> CompletableFuture<T>.awaitNoDMT(): T {
  if (this is DebuggerCompletableFuture) {
    // Alternatively, we could check the thread in `this.thenApply {...}`.
    // But such a solution should also check whether `await` was already called, as the future can be completed before that.
    // In that case `thenApply` can be called directly in the current thread.
    fileLogger().error("Should not wait on DTM, ensure com.intellij.debugger.impl.DebuggerUtilsAsync.reschedule is not used for the future")
  }
  return await()
}
