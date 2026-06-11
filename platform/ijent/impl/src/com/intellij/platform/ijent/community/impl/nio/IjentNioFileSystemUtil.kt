// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentNioFileSystemUtil")
@file:ApiStatus.Internal

package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.prepareThreadContext
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOwnedBuilder
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFsError
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import com.intellij.platform.ijent.IjentCalledContextElement
import com.intellij.platform.ijent.IjentCallerContext
import com.intellij.platform.ijent.allowCancellableNio
import com.intellij.platform.ijent.unavailableDialogTimeout
import com.intellij.util.IntelliJCoroutinesFacade
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

internal fun Path.toEelPath(): EelPath =
  when {
    this is AbsoluteIjentNioPath -> eelPath
    else -> throw IllegalArgumentException("$this is not absolute IjentNioPath")
  }

@ApiStatus.Internal
fun <F : EelFileSystemApi, T> F.fsBlocking(body: suspend F.() -> T): T {
  return descriptor.fsBlocking {
    body()
  }
}

@ApiStatus.Internal
fun <T> EelPath.fsBlocking(body: suspend () -> T): T {
  return descriptor.fsBlocking {
    body()
  }
}


@ApiStatus.Internal
fun <T, E : EelFsError, O : EelOwnedBuilder<EelResult<T, E>>> O.getOrThrowFileSystemExceptionBlocking(): T {
  return eelDescriptor.fsBlocking { getOrThrowFileSystemException() }
}

/**
 * Bridges synchronous NIO into IJent coroutines using [runBlocking].
 *
 * Has the following features:
 * - The coroutine is processed in-place in a caller thread.
 * - Creates a fresh isolated event loop instead of reusing the caller's thread-local one
 *    (avoids stealing tasks from an outer event loop in case of nested runBlocking).
 * - Shows a modal dialog if ijent is not responding long, but only when the EDT is free to display it;
 *   when fsBlocking blocks the EDT itself no dialog is shown and the thread just waits (see IJPL-245001).
 * - Is ready for being called in all reasonable contexts:
 *   - blocking context or coroutine context,
 *   - read actions, write actions,
 *   - background or event dispatch thread,
 *   - inside runBlocking or runBlockingCancellable
 * - But fsBlocking itself should never form nested calls (only ijent calls inside or ijent deployment, nothing else).
 *
 * Should be used only for adapting EEL operations into nio-style api.
 * Should be used instead of plain [runBlocking] or [com.intellij.openapi.progress.runBlockingMaybeCancellable] for all ijent operation
 * `...Blocking()` wrappers because here timeouts and modal dialog are integrated.
 *
 * [runAndCompensateParallelism] is a last-resort safety net against [Dispatchers.Default] starvation:
 * if all pool threads block here simultaneously while body() dispatches work to [Dispatchers.Default]
 * internally, no thread remains to run it — extra workers are spawned after 500 ms.
 * If without the compensation there are still deadlocks, that indicates a bug:
 * body() should never dispatch to [Dispatchers.Default] or await coroutines from there.
 */
@ApiStatus.Internal
fun <T> EelDescriptor.fsBlocking(body: suspend () -> T): T {
  // Block the calling thread directly; intentionally do NOT pump the event queue while waiting for Eel.
  //
  // IJPL-246172 tried to free the EDT here via runWithModalProgressBlocking so that remote file systems (e.g. SSH) could show dialogs.
  // Pumping the event queue on the EDT is re-entrant, though: a queued Settings-tree repaint instantiates a Configurable whose blocking
  // service initialization parks the EDT, freezing the IDE (IJPL-247000). Reverted until a non-re-entrant way to free the EDT exists.
  //
  // Trade-off: while a remote FS is slow or wants to show UI the EDT stays blocked (the original IJPL-245001 problem).
  // That is preferable to the hard freeze and is tracked in IJPL-245001.
  return IntelliJCoroutinesFacade.runAndCompensateParallelism(500.milliseconds) {
    fsBlockingWithoutParallelismCompensation {
      showModalDialogOnTimeout(this, IjentCallerContext.computeCallerContext().unavailableDialogTimeout()) {
        body()
      }
    }
  }
}

fun IjentCallerContext.Companion.computeCallerContext(): IjentCallerContext {
  val application = ApplicationManager.getApplication()
  return IjentCallerContext(
    isRead = application.isReadAccessAllowed,
    isWrite = application.isWriteAccessAllowed,
    isDispatchThread = application.isDispatchThread
  )
}

@Suppress("RAW_RUN_BLOCKING")
@VisibleForTesting
@ApiStatus.Internal
fun <T> fsBlockingWithoutParallelismCompensation(body: suspend (IjentCallerContext) -> T): T {
  val callerContext = IjentCallerContext.computeCallerContext()
  if (callerContext.allowCancellableNio()) {
    return prepareThreadContext { ctx ->
      runBlocking(ctx + IjentCalledContextElement(callerContext) + NestedBlockingEventLoop(Thread.currentThread())) {
        body(callerContext)
      }
    }
  }
  return runBlocking(IjentCalledContextElement(callerContext) + NestedBlockingEventLoop(Thread.currentThread())) {
    body(callerContext)
  }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER", "ERROR_SUPPRESSION")
private class NestedBlockingEventLoop(override val thread: Thread) : kotlinx.coroutines.EventLoopImplBase() {
  override fun shouldBeProcessedFromContext(): Boolean = true
}