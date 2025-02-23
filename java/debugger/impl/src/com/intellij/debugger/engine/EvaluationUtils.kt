// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.breakpoints.FilteredRequestor
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.request.EventRequest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration

/**
 * Find some suspend context in which evaluation is possible.
 * Then evaluate the given action using the found context.
 *
 * Throws [kotlinx.coroutines.TimeoutCancellationException] if fails to get proper context in the given amount of [timeToSuspend].
 */
@ApiStatus.Experimental
internal suspend fun <R> suspendAllAndEvaluate(
  context: DebuggerContextImpl,
  timeToSuspend: Duration,
  action: (SuspendContextImpl) -> R
): R {
  val process = context.debugProcess!!
  val suspendContext = context.suspendContext
  return if (suspendContext == null) {
    // Not suspended at all.
    tryToBreakOnAnyMethodAndEvaluate(context, process, timeToSuspend, action)

  }
  else if (process.isEvaluationPossible(suspendContext)) {
    // We are on a breakpoint, we can evaluate right here.
    val result = Channel<R>(capacity = 1)

    // We have to evaluate inside SuspendContextCommandImpl, so we just start a new command.
    // TODO: are there any better ways to do this? Should we create proper command above?
    executeOnDMT(suspendContext) {
      result.send(action(suspendContext))
    }

    result.receive()
  }
  else {
    // We are on a pause, cannot evaluate.
    tryToResumeThenBreakOnAnyMethodAndEvaluate(context, process, suspendContext, timeToSuspend, action)
  }

}

// FIXME: too much copypasted, compare and merge
private suspend fun <R> tryToBreakOnAnyMethodAndEvaluate(
  context: DebuggerContextImpl,
  process: DebugProcessImpl,
  timeToSuspend: Duration,
  action: (SuspendContextImpl) -> R
): R {
  val evaluatableContextResult = Channel<SuspendContextImpl>(capacity = 1)

  // Create a request which suspends all the threads and gets the suspendContext.
  val requestor = object : FilteredRequestor {
    override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
      process.requestsManager.deleteRequest(this)
      val evaluatableContext = action.suspendContext!!
      evaluatableContextResult.trySend(evaluatableContext).also { assert(it.isSuccess) }
      return true
    }

    override fun getSuspendPolicy(): String = DebuggerSettings.SUSPEND_ALL
  }

  val request = process.requestsManager.createMethodEntryRequest(requestor)
  request.setSuspendPolicy(EventRequest.SUSPEND_ALL)
  request.isEnabled = true

  val evaluatableContext = try {
    withTimeout(timeToSuspend) {
      evaluatableContextResult.receive()
    }
  }
  finally {
    process.requestsManager.deleteRequest(requestor)
  }

  try {
    return action(evaluatableContext)
  }
  finally {
    context.managerThread!!
      .invokeNow(process.createResumeCommand(evaluatableContext))
  }
}

private suspend fun <R> tryToResumeThenBreakOnAnyMethodAndEvaluate(
  context: DebuggerContextImpl,
  process: DebugProcessImpl,
  pauseSuspendContext: SuspendContextImpl,
  timeToSuspend: Duration,
  action: (SuspendContextImpl) -> R
): R {
  val evaluatableContextResult = Channel<SuspendContextImpl>(capacity = 1)

  // Create a request which suspends all the threads and gets the suspendContext.
  val requestor = object : FilteredRequestor {
    override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
      process.requestsManager.deleteRequest(this)
      val evaluatableContext = action.suspendContext!!
      evaluatableContextResult.trySend(evaluatableContext).also { assert(it.isSuccess) }
      return true
    }

    override fun getSuspendPolicy(): String = DebuggerSettings.SUSPEND_ALL
  }

  val request = process.requestsManager.createMethodEntryRequest(requestor)
  request.setSuspendPolicy(EventRequest.SUSPEND_ALL)
  request.isEnabled = true

  context.managerThread!!
    .invokeNow(process.createResumeCommand(pauseSuspendContext))

  val evaluatableContext = try {
    withTimeout(timeToSuspend) {
      evaluatableContextResult.receive()
    }
  }
  catch (e: TimeoutCancellationException) {
    // FIXME: get preferred thread from pauseSuspendContext
    context.managerThread!!
      .invokeNow(process.createPauseCommand(null))
    throw e
  }
  finally {
    process.requestsManager.deleteRequest(requestor)
  }

  return action(evaluatableContext)
}
