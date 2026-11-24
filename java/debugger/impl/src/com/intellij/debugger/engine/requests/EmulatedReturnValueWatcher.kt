// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.MethodBytecodeUtil
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ThreadReference
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.VoidValue
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.Event
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodEntryEvent
import com.sun.jdi.event.MethodExitEvent
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.EventRequestManager
import kotlinx.coroutines.CancellationException

/**
 * Captures return values with return breakpoint requests, not with an exit event for each call.
 * It follows only the base frame and its callees, see [CaptureScope].
 *
 * @author Yude Jiang
 */
class EmulatedReturnValueWatcher(private val requestManager: EventRequestManager) : ReturnValueWatcher {
  override var lastMethodReturnValue: MethodReturnValue? = null
  private var state: State = State.Idle

  private lateinit var thread: ThreadReference
  private lateinit var baseMethod: Method
  private var baseDepth = 0
  private var baseReturnBreakpointRequests: List<BreakpointRequest> = emptyList()

  private val returnLocations = HashMap<Method, Collection<Location>>()

  /** Each state owns its requests. A transition deletes them. */
  private sealed class State(val requests: List<EventRequest>) {
    /** No capture runs. The watcher ignores late events. */
    object Idle : State(emptyList())

    /** Waits for a callee entry or a base return breakpoint. */
    class FollowBase(entryRequest: EventRequest) : State(listOf(entryRequest))

    /** Waits for a return breakpoint of [callee] or of the base method. */
    class FollowCall(val callee: Method, returnBreakpointRequests: List<EventRequest>) : State(returnBreakpointRequests)

    /** Waits for the exit of [callee] to record its return value. */
    class RecordCall(val callee: Method, exitRequest: EventRequest) : State(listOf(exitRequest))

    /** Waits for a base return breakpoint ([CaptureScope.BASE_FRAME]). */
    object IgnoreCall : State(emptyList())

    /** Waits for the exit of the base method to record its return value and end the capture. */
    class RecordBase(exitRequest: EventRequest) : State(listOf(exitRequest))
  }

  override fun startCapture(thread: ThreadReference, scope: CaptureScope) {
    // The previous capture can still run.
    endCapture()
    lastMethodReturnValue = null

    failSafe({ "start a capture" }) {
      this.thread = thread
      val startLocation = thread.frame(0).location()
      baseMethod = startLocation.method()
      baseDepth = thread.frameCount()

      // A step out of a void base method records no value, so it needs no base return breakpoint requests.
      baseReturnBreakpointRequests =
        if (scope == CaptureScope.BASE_FRAME && DebuggerUtilsEx.isVoid(baseMethod)) emptyList() else newReturnBreakpointRequests(baseMethod)

      // A breakpoint request at the start location never fires, so a capture that starts at a return instruction waits for the exit.
      if (baseReturnBreakpointRequests.isNotEmpty() && baseMethod.returnsAt(startLocation.codeIndex())) {
        transitionTo(State.RecordBase(newExitRequest()))
      }
      else if (scope == CaptureScope.BASE_FRAME) {
        transitionTo(State.IgnoreCall)
      }
      else {
        transitionTo(State.FollowBase(newEntryRequest()))
      }
    }
  }

  override fun stopCapture() {
    endCapture()
  }

  override fun clear() {
    lastMethodReturnValue = null
  }

  override fun processEvent(event: Event): Boolean {
    if (!ReturnValueWatcher.isWatcherEvent(event)) return false

    // A failure must not escape, because the event loop resumes the event set only after this function returns.
    failSafe({ "process $event" }) {
      when (val s = state) {
        State.Idle -> Unit

        // No callee runs here, so each entry is a direct callee and each breakpoint is at baseDepth.
        // A native or empty callee has no return breakpoint that can fire, so the capture waits for its exit.
        is State.FollowBase if event is MethodEntryEvent &&
          (event.method().isNative || event.method().returnsAt(event.location().codeIndex())) ->
          transitionTo(State.RecordCall(event.method(), newExitRequest()))
        is State.FollowBase if event is MethodEntryEvent -> {
          val callee = event.method()
          val requests =
            if (callee == baseMethod && baseReturnBreakpointRequests.isNotEmpty()) emptyList() else newReturnBreakpointRequests(callee)

          transitionTo(State.FollowCall(callee, requests))
        }
        is State.FollowBase if event is BreakpointEvent -> transitionTo(State.RecordBase(newExitRequest()))

        is State.FollowCall if event is BreakpointEvent -> when (event.depth) {
          baseDepth + 1 if (DebuggerUtilsEx.isVoid(s.callee) || isJvmInternal(s.callee)) ->
            transitionTo(State.FollowBase(newEntryRequest()))
          baseDepth + 1 -> transitionTo(State.RecordCall(s.callee, newExitRequest()))
          // The base frame returns, so an exception popped the callee.
          baseDepth -> transitionTo(State.RecordBase(newExitRequest()))
          // A recursive call of the callee returns.
          else -> Unit
        }

        is State.RecordCall if event is MethodExitEvent && event.method() == s.callee -> {
          record(event)
          transitionTo(State.FollowBase(newEntryRequest()))
        }
        // A method that a native callee calls returns.
        is State.RecordCall if event is MethodExitEvent && s.callee.isNative -> Unit

        // Callees run in IgnoreCall, so a recursive call of the base method can hit the base return breakpoint requests.
        is State.IgnoreCall if event is BreakpointEvent && event.depth == baseDepth -> transitionTo(State.RecordBase(newExitRequest()))
        is State.IgnoreCall if event is BreakpointEvent -> Unit

        is State.RecordBase if event is MethodExitEvent && event.method() == baseMethod -> {
          // Callee values belong to the base frame, so a void base method leaves no value.
          clear()
          record(event)
          endCapture()
        }

        else -> warnUnexpected(event)
      }
    }
    return true
  }

  /** Runs [action]. On a failure, logs it and ends the capture. A VM disconnect and a cancellation pass through. */
  private inline fun failSafe(what: () -> String, action: () -> Unit) {
    try {
      action()
    }
    catch (e: Exception) {
      if (e is VMDisconnectedException || e is ControlFlowException || e is CancellationException) throw e
      fileLogger().warn("Cannot ${what()}, the capture ends", e)
      endCapture()
    }
  }

  private fun record(event: MethodExitEvent) {
    val value = event.returnValue()
    if (value !is VoidValue) {
      lastMethodReturnValue = MethodReturnValue(event.method(), value)
    }
  }

  private fun transitionTo(next: State) {
    deleteRequests(state.requests)
    state = next
  }

  /** The only way into [State.Idle]. Also deletes the base return breakpoint requests, which live for the whole capture. */
  private fun endCapture() {
    transitionTo(State.Idle)
    deleteRequests(baseReturnBreakpointRequests)
    baseReturnBreakpointRequests = emptyList()
  }

  private fun warnUnexpected(event: Event) {
    val expected = when (val s = state) {
      State.Idle -> "no event"
      is State.FollowBase -> "a method entry or a return of $baseMethod"
      is State.FollowCall -> "a return of ${s.callee} or of $baseMethod"
      is State.RecordCall -> "the exit of ${s.callee}"
      State.IgnoreCall -> "a return of $baseMethod"
      is State.RecordBase -> "the exit of $baseMethod"
    }
    val method = (event as? LocatableEvent)?.location()?.method()
    fileLogger().warn("Unexpected $event of $method in the state ${state.javaClass.simpleName}, expected $expected")
  }

  private val LocatableEvent.depth: Int
    get() = thread().frameCount()

  private fun returnLocationsOf(method: Method): Collection<Location> {
    val cached = returnLocations[method]
    // A HotSwap can move the return instructions. The redefined method keeps its ID, so it is equal to the old key.
    // JDI makes a new Method instance for it, so the locations of another instance are stale.
    if (cached != null && cached.firstOrNull()?.method() === method) return cached
    return MethodBytecodeUtil.getMethodExitLocations(method).also { returnLocations[method] = it }
  }

  private fun Method.returnsAt(codeIndex: Long): Boolean = returnLocationsOf(this).any { it.codeIndex() == codeIndex }

  /**
   * Returns true for JVM-internal callees whose return values are noise.
   * All matched methods are non-native, so [State.FollowCall] handles them.
   * Requires both owner and method to be non-public in java.lang.invoke so public API overrides stay visible.
   */
  private fun isJvmInternal(method: Method): Boolean {
    val owner = method.declaringType()
    val ownerName = owner.name()
    if (ownerName.startsWith("java.lang.invoke.")) return !owner.isPublic && !method.isPublic
    if (ownerName == "sun.instrument.InstrumentationImpl") return method.name() == "transform"
    if (method.name() == "loadClass" && method.signature() == "(Ljava/lang/String;)Ljava/lang/Class;") {
      return DebuggerUtils.instanceOf(owner, "java.lang.ClassLoader")
    }
    return false
  }

  private fun newEntryRequest(): EventRequest = requestManager.createMethodEntryRequest().register { addThreadFilter(thread) }

  private fun newExitRequest(): EventRequest = requestManager.createMethodExitRequest().register { addThreadFilter(thread) }

  /** Creates the return breakpoint requests of the method, one per return instruction. On a failure, deletes the ones it created. */
  private fun newReturnBreakpointRequests(method: Method): List<BreakpointRequest> {
    val requests = ArrayList<BreakpointRequest>()
    try {
      for (location in returnLocationsOf(method)) {
        requests.add(requestManager.createBreakpointRequest(location).register { addThreadFilter(thread) })
      }
    }
    catch (e: Exception) {
      deleteRequests(requests)
      throw e
    }
    return requests
  }

  /** Configures and enables a new request. On a failure, deletes it. */
  private inline fun <T : EventRequest> T.register(configure: T.() -> Unit): T {
    try {
      configure()
      putProperty(WATCHER_REQUEST_KEY, true)
      setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
      DebuggerUtilsAsync.setEnabled(this, true)
    }
    catch (e: Exception) {
      DebuggerUtilsAsync.deleteEventRequest(requestManager, this)
      throw e
    }
    return this
  }

  private fun deleteRequests(requests: List<EventRequest>) {
    requests.forEach { DebuggerUtilsAsync.deleteEventRequest(requestManager, it) }
  }
}
