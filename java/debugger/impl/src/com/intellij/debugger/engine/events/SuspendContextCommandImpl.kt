// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.events

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import org.jetbrains.annotations.ApiStatus

private val LOG = Logger.getInstance(SuspendContextCommandImpl::class.java)

/**
 * Performs contextAction when evaluation is available in suspend context
 */
abstract class SuspendContextCommandImpl protected constructor(open val suspendContext: SuspendContextImpl?) : DebuggerCommandImpl() {
  private var mySuspendContextSetInProgress = false

  @Throws(Exception::class)
  open fun contextAction(suspendContext: SuspendContextImpl) {
    throw AbstractMethodError()
  }

  @ApiStatus.Experimental
  open suspend fun contextActionSuspend(suspendContext: SuspendContextImpl) = contextAction(suspendContext)

  override suspend fun actionSuspend() {
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

    invokeWithChecks(suspendContext) {
      if (LOG.isDebugEnabled) {
        LOG.debug("Executing suspend-context-command: $this")
      }
      contextActionSuspend(suspendContext)
    }
    check(resetContinuation(null) == null) { "Continuation is not null after resume" }
  }

  override fun invokeContinuation() {
    val suspendContext = suspendContext ?: error("SuspendContext is null, while is must be checked at the command start")
    runBlockingMaybeCancellable {
      // no actual suspension inside
      invokeWithChecks(suspendContext) {
        executeContinuation()
      }
    }
  }

  private suspend fun invokeWithChecks(suspendContext: SuspendContextImpl, operation: suspend () -> Unit) {
    if (suspendContext.myInProgress) {
      suspendContext.postponeCommand(this)
    }
    else {
      try {
        if (!suspendContext.isResumed) {
          if (suspendContext.myInProgress) {
            LOG.error("Suspend context is already in progress", ThreadDumper.dumpThreadsToString())
          }
          suspendContext.myInProgress = true
          mySuspendContextSetInProgress = true
          operation()
        }
        else {
          notifyCancelled()
        }
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
            suspendContext.debugProcess.managerThread.pushBack(postponed)
          }
        }
      }
    }
  }

  override fun onSuspendOrFinish() {
    if (mySuspendContextSetInProgress) {
      suspendContext?.myInProgress = false
    }
  }
}
