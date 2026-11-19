// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests

import com.sun.jdi.Method
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value
import com.sun.jdi.event.Event

/** A method and its return value. */
data class MethodReturnValue(val method: Method, val value: Value?)

/**
 * What a capture records.
 * The base frame is the frame where the capture starts. A callee is a method that the base frame calls directly.
 */
enum class CaptureScope {
  /** The return values of the base frame and of its callees. */
  WITH_CALLS,

  /** Only the return value of the base frame. */
  BASE_FRAME,
}

/**
 * Captures method return values between [startCapture] and [stopCapture].
 *
 * Call all functions on the debugger manager thread. The caller checks the user setting before it starts a capture.
 */
interface ReturnValueWatcher {
  /** The last non-void return value of the capture, or null. */
  val lastMethodReturnValue: MethodReturnValue?

  /** Starts a capture in the thread. A watcher can record more than the [scope] requires. */
  fun startCapture(thread: ThreadReference, scope: CaptureScope)

  /** Stops the capture and keeps the last return value. Safe to call when no capture runs. */
  fun stopCapture()

  /** Forgets the last return value. */
  fun clear()

  /** Returns true if the watcher claims the event. A watcher claims its events also after the capture stopped. */
  fun processEvent(event: Event): Boolean

  companion object {
    /** Returns true if a [ReturnValueWatcher] created the request of the event. Such an event never stops the debugger. */
    @JvmStatic
    fun isWatcherEvent(event: Event): Boolean = event.request()?.getProperty(WATCHER_REQUEST_KEY) != null
  }
}

/** The property that marks each request of a [ReturnValueWatcher]. */
internal const val WATCHER_REQUEST_KEY: String = "WATCHER_REQUEST_KEY"
