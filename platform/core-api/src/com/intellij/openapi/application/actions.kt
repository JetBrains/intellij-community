// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.Computable
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete

/** Use [edtWriteAction]. */
fun <T> runWriteAction(runnable: () -> T): T {
  return ApplicationManager.getApplication().runWriteAction(Computable(runnable))
}

fun <T> runUndoTransparentWriteAction(runnable: () -> T): T {
  return CommandProcessor.getInstance().withUndoTransparentAction().use {
    ApplicationManager.getApplication().runWriteAction(Computable(runnable))
  }
}

/**
 * @see ReadAction.nonBlocking for background processing without suspend
 * @see NonBlockingReadAction.executeSynchronously for synchronous execution in background threads
 * @see readAction for suspend contexts
 * @see runReadActionBlocking for explicitly non-cancellable read actions, avoid using in background threads
 */
@Deprecated("Use ReadAction.nonBlocking or runReadActionBlocking (for explicitly non-cancellable read actions)")
fun <T> runReadAction(runnable: () -> T): T = runReadActionBlocking(runnable)

/**
 * Runs the specified computation in a blocking read action (as opposed to [NonBlockingReadAction]).
 * Can be called from any thread. Do not get canceled if a write action is pending, executed at most once.
 *
 * Avoid usage in background threads as it will likely cause UI freezes. Use it only under modal progress or from [EDT].
 *
 * The computation is executed immediately if no write action is currently running or the write action is running on the current thread.
 * Otherwise, the action is **blocked** until the currently running write action completes.
 *
 * @see ReadAction.nonBlocking for background processing without suspend
 * @see NonBlockingReadAction.executeSynchronously() for synchronous execution in background threads
 * @see readAction for suspend contexts
 */
@RequiresBlockingContext
fun <T> runReadActionBlocking(runnable: () -> T): T {
  return ApplicationManager.getApplication().runReadAction(Computable(runnable))
}

/**
 * @suppress Internal use only
 */
@Internal
fun <T> invokeAndWaitIfNeeded(modalityState: ModalityState? = null, runnable: () -> T): T {
  val app = ApplicationManager.getApplication()
  if (app.isDispatchThread) {
    return runnable()
  }
  else {
    var resultRef: T? = null
    app.invokeAndWait({ resultRef = runnable() }, modalityState ?: ModalityState.defaultModalityState())
    @Suppress("UNCHECKED_CAST")
    return resultRef as T
  }
}

fun runInEdt(modalityState: ModalityState? = null, runnable: () -> Unit) {
  val app = ApplicationManager.getApplication()
  if (app.isDispatchThread) {
    runnable()
  }
  else {
    invokeLater(modalityState, runnable)
  }
}

@Obsolete
fun invokeLater(modalityState: ModalityState? = null, runnable: () -> Unit) {
  ApplicationManager.getApplication().invokeLater({ runnable() }, modalityState ?: ModalityState.defaultModalityState())
}
