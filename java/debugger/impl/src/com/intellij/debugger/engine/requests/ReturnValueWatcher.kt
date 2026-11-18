// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests

import com.sun.jdi.Method
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value
import com.sun.jdi.event.Event

/** A method and the value that it returned. */
data class MethodReturnValue(val method: Method, val value: Value?)


/**
 * Captures method return values between [startCapture] and [stopCapture].
 * The caller checks the user setting before it starts a capture.
 */
interface ReturnValueWatcher {
  // All these functions must only be called if the feature is enabled in settings.

  /** The last non-void method that returned during the capture, or null. */
  val lastMethodReturnValue: MethodReturnValue?

  /** Starts to capture return values in the thread. */
  fun startCapture(thread: ThreadReference)

  /** Stops the capture. Keeps the last value. Safe to call when the capture is not active. */
  fun stopCapture()

  /** Forgets the last value. */
  fun clear()

  /** Returns true if the event belongs to this watcher. Claims its own events also when the capture is not active. */
  fun processEvent(event: Event): Boolean

  companion object {
    const val WATCHER_REQUEST_KEY: String = "WATCHER_REQUEST_KEY"
  }
}
