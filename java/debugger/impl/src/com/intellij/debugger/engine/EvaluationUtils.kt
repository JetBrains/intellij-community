// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.breakpoints.FilteredRequestor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.impl.breakpoints.BreakpointState
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.Type
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.request.EventRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import kotlin.time.Duration
import org.jetbrains.org.objectweb.asm.Type as AsmType

/**
 * Find some suspend context in which evaluation is possible.
 * Then evaluate the given action using the found context.
 *
 * Throws [kotlinx.coroutines.TimeoutCancellationException] if fails to get proper context in the given amount of [timeToSuspend].
 */
@ApiStatus.Experimental
@ApiStatus.Internal
suspend fun <R> suspendAllAndEvaluate(
  context: DebuggerContextImpl,
  timeToSuspend: Duration,
  action: suspend (SuspendContextImpl) -> R,
): R {
  DebuggerManagerThreadImpl.assertIsManagerThread()
  val process = context.debugProcess!!
  val suspendContext = context.suspendContext
  return if (suspendContext != null
             && process.isEvaluationPossible(suspendContext)
             && suspendContext.suspendPolicy == EventRequest.SUSPEND_ALL) {
    // We are on a Suspend All breakpoint, we can evaluate right here.
    withDebugContext(suspendContext) {
      action(suspendContext)
    }
  }
  else {
    val pauseSuspendContext = suspendContext?.takeIf { !process.isEvaluationPossible(it) }
    // The current context does not fit, try to evaluate on a breakpoint.
    tryToBreakOnAnyMethodAndEvaluate(context, process, pauseSuspendContext, timeToSuspend, action)
  }
}

private suspend fun <R> tryToBreakOnAnyMethodAndEvaluate(
  context: DebuggerContextImpl,
  process: DebugProcessImpl,
  pauseSuspendContext: SuspendContextImpl?,
  timeToSuspend: Duration,
  actionToEvaluate: suspend (SuspendContextImpl) -> R,
): R {
  val onPause = pauseSuspendContext != null

  var timedOut = false

  val programSuspendedActionStarted = CompletableDeferred<Unit>()
  val actionResult = CompletableDeferred<R>()

  // Create a request which suspends all the threads and gets the suspendContext.
  val requestor = object : FilteredRequestor {
    override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
      process.requestsManager.deleteRequest(this)
      if (!timedOut) {
        val suspendContext = action.suspendContext!!
        programSuspendedActionStarted.complete(Unit)
        actionResult.completeWith(runCatching {
          runBlockingCancellable {
            actionToEvaluate(suspendContext)
          }
        })
      }
      // Note: in case the context was not originally suspended, return false,
      // so that suspendContext is resumed when action is computed,
      // thus no suspension will be visible in the UI
      return onPause
    }

    override fun getSuspendPolicy(): String = DebuggerSettings.SUSPEND_ALL
  }

  // We prefer to stop at a predictable location (i.e., SuspendHelper)
  // to prevent any deadlocks by inconsistent state of VirtualThread/Coroutine
  // See IDEA-370914.
  val suspendHelperMethod = process
    .takeIf { AsyncStacksUtils.isSuspendHelperEnabled() }
    ?.findLoadedClass(null, "com.intellij.rt.debugger.agent.SuspendHelper", null)
    ?.let { DebuggerUtils.findMethod(it, "suspendHelperLoopBody", "()V") }

  val request =
    if (suspendHelperMethod != null) process.requestsManager.createBreakpointRequest(requestor, suspendHelperMethod.locationOfCodeIndex(0))
    else process.requestsManager.createMethodEntryRequest(requestor)
  request.setSuspendPolicy(EventRequest.SUSPEND_ALL)
  DebuggerUtilsAsync.setEnabled(request, true)

  // If the context was on pause, it should be resumed first to hit the breakpoint
  if (onPause) {
    context.managerThread!!
      .invokeNow(process.createResumeCommand(pauseSuspendContext))
  }

  // Check that we hit the breakpoint within the specified timeout
  try {
    withTimeout(timeToSuspend) {
      programSuspendedActionStarted.await()
    }
  }
  catch (e: TimeoutCancellationException) {
    // Try to make it earlier.
    process.requestsManager.deleteRequest(requestor)

    withDebugContext(context.managerThread!!) {
      // FIXME: unify all this logic with evaluatable Pause
      timedOut = true
      if (programSuspendedActionStarted.isCompleted) {
        // Request was already processed, we need to ignore the timeout.
      }
      else {
        if (onPause) {
          // FIXME: get preferred thread from pauseSuspendContext
          // If the context was originally on pause, but after resume did not hit a breakpoint within a timeout,
          // then it should be paused again
          process.createPauseCommand(null).invokeCommand()
        }
        throw e
      }
    }
  }
  finally {
    process.requestsManager.deleteRequest(requestor)
  }

  return actionResult.await()
}

@ApiStatus.Internal
fun <Self : XBreakpoint<P>, P : XBreakpointProperties<*>, S : BreakpointState> shouldInstrumentBreakpoint(xB: XBreakpointBase<Self, P, S>): Boolean {
  if (!XBreakpointUtil.isBreakpointInstrumentationSwitchedOn()) {
    return false
  }
  if (xB.isLogMessage || xB.isLogStack) return false
  val properties = xB.properties
  if (properties !is JavaLineBreakpointProperties) return false

  // Do not use instrumentation for non-standard breakpoints: any filters will back up to the old behavior
  if (JavaLineBreakpointProperties() != properties) return false

  val isLoggingBp = xB.logExpressionObject != null && xB.suspendPolicy == SuspendPolicy.NONE
  val isConditionalBp = xB.conditionExpression != null && xB.isConditionEnabled
  return (isLoggingBp || isConditionalBp) && !(isLoggingBp && isConditionalBp)
}

@ApiStatus.Internal
fun Type.isSubtype(className: String): Boolean = isSubtype(AsmType.getObjectType(className))

@ApiStatus.Internal
fun Type.isSubTypeOrSame(className: String): Boolean =
  name() == className || isSubtype(className)

@ApiStatus.Internal
fun Type.isSubtype(type: AsmType): Boolean {
  if (this.signature() == type.descriptor) {
    return true
  }

  if (type.sort != AsmType.OBJECT || this !is ClassType) {
    return false
  }

  val superTypeName = type.className

  if (allInterfaces().any { it.name() == superTypeName }) {
    return true
  }

  var superClass = superclass()
  while (superClass != null) {
    if (superClass.name() == superTypeName) {
      return true
    }
    superClass = superClass.superclass()
  }

  return false
}

@ApiStatus.Internal
enum class ClientEvaluationExceptionType {
  USER_EXCEPTION,
  MISCOMPILED,
  ERROR_DURING_PARSING_EXCEPTION
}

@ApiStatus.Internal
fun extractTypeFromClientException(exceptionFromCodeFragment: ObjectReference, hasCast: Boolean): ClientEvaluationExceptionType {
  try {
    val type = exceptionFromCodeFragment.type()
    if (type.signature().equals("Ljava/lang/IllegalArgumentException;")) {
      if (DebuggerUtils.tryExtractExceptionMessage(exceptionFromCodeFragment) == "argument type mismatch") {
        return ClientEvaluationExceptionType.MISCOMPILED
      }
    }
    if (type.signature().startsWith("Ljava/lang/invoke/")
        || type.isSubTypeOrSame("java.lang.ReflectiveOperationException")
        || type.isSubTypeOrSame("java.lang.LinkageError")
    ) {
      return ClientEvaluationExceptionType.MISCOMPILED
    }
    if (type.isSubTypeOrSame("java.lang.ClassCastException")) {
      return if (hasCast) ClientEvaluationExceptionType.USER_EXCEPTION else ClientEvaluationExceptionType.MISCOMPILED
    }
    return ClientEvaluationExceptionType.USER_EXCEPTION
  } catch (e: Throwable) {
    logger<DebugProcessImpl>().error("Can't extract error type from InvocationException", e)
    return ClientEvaluationExceptionType.ERROR_DURING_PARSING_EXCEPTION
  }
}
