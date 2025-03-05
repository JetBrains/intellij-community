// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.events

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus

private val LOG = Logger.getInstance(SuspendContextCommandImpl::class.java)

/**
 * Performs contextAction when evaluation is available in suspend context
 */
abstract class SuspendContextCommandImpl protected constructor(open val suspendContext: SuspendContextImpl?) : DebuggerCommandImpl() {
  private var mySuspendContextSetInProgress = false
  private var shouldCancelCommandScopeOnSuspend = false

  @Throws(Exception::class)
  open fun contextAction(suspendContext: SuspendContextImpl): Unit = throw AbstractMethodError()

  @ApiStatus.Experimental
  open suspend fun contextActionSuspend(suspendContext: SuspendContextImpl): Unit = contextAction(suspendContext)

  final override suspend fun actionSuspend() {
    if (LOG.isDebugEnabled) {
      LOG.debug("trying $this")
    }

    val suspendContext = suspendContext
    if (suspendContext == null) {
      if (LOG.isDebugEnabled) {
        LOG.debug("skip processing - context is null $this")
      }
      notifyCancelled()
      return
    }

    try {
      suspendContext.addUnfinishedCommand(this)
      invokeWithChecks {
        if (LOG.isDebugEnabled) {
          LOG.debug("Executing suspend-context-command: $this")
        }
        contextActionSuspend(suspendContext)
      }
    }
    finally {
      suspendContext.removeUnfinishedCommand(this)
    }
  }

  /**
   * When [SuspendContextImpl] is resumed, the command scope of the unfinished commands should be canceled.
   * However, cancellation of the scope for the current command can affect its execution,
   * so the cancellation of the current command is postponed upon its suspension.
   */
  @ApiStatus.Internal
  fun cancelCommandScopeOnSuspend() {
    shouldCancelCommandScopeOnSuspend = true
  }

  final override fun invokeContinuation(): Unit = invokeWithChecks {
    executeContinuation()
  }

  private inline fun invokeWithChecks(operation: () -> Unit) {
    val suspendContext = suspendContext ?: error("SuspendContext is null, while is must be checked at the command start")
    if (suspendContext.myInProgress) {
      suspendContext.postponeCommand(this)
      return
    }
    try {
      if (suspendContext.isResumed) {
        notifyCancelled()
        return
      }

      if (suspendContext.myInProgress) {
        LOG.error("Suspend context is already in progress", ThreadDumper.dumpThreadsToString())
      }
      suspendContext.myInProgress = true
      mySuspendContextSetInProgress = true
      operation()
    }
    finally {
      suspendContext.myInProgress = false
      mySuspendContextSetInProgress = false
      if (suspendContext.isResumed) {
        suspendContext.cancelAllPostponed()
      }
      else {
        val postponed = suspendContext.pollPostponedCommand()
        if (postponed != null) {
          suspendContext.managerThread.pushBack(postponed)
        }
      }
    }
  }

  final override fun onSuspendOrFinish() {
    if (mySuspendContextSetInProgress) {
      mySuspendContextSetInProgress = false
      suspendContext?.myInProgress = false
    }
    if (shouldCancelCommandScopeOnSuspend) {
      shouldCancelCommandScopeOnSuspend = false
      cancelCommandScope()
    }
  }
}
