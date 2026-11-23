// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.overhead.OverheadProducer
import com.intellij.debugger.ui.overhead.OverheadTimings
import com.intellij.icons.AllIcons
import com.sun.jdi.ThreadReference
import com.sun.jdi.event.Event
import java.util.concurrent.TimeUnit

/**
 * Reports the cost of a [ReturnValueWatcher] to the Overhead view, once per capture.
 *
 * A hit is a capture in which the watcher claimed an event. The time is the debugger time in the watcher,
 * without the time in the VM and in the transport. The Overhead view stores whole milliseconds
 * and treats many small reports as excessive, so one report per capture is necessary.
 */
internal class OverheadReportingWatcher(
  private val delegate: ReturnValueWatcher,
  private val process: DebugProcess,
) : ReturnValueWatcher by delegate {
  private var claimedEvent = false
  private var nanos = 0L

  override fun startCapture(thread: ThreadReference, scope: CaptureScope) {
    // A capture can end without stopCapture, for example after a pause.
    report()
    measure { delegate.startCapture(thread, scope) }
  }

  override fun stopCapture() {
    measure { delegate.stopCapture() }
    report()
  }

  override fun processEvent(event: Event): Boolean {
    val start = System.nanoTime()
    val claimed = delegate.processEvent(event)
    if (claimed) {
      claimedEvent = true
      nanos += System.nanoTime() - start
    }
    return claimed
  }

  /** Reports the work since the last report, if there was any. Keeps the time below 1 ms for the next report. */
  private fun report() {
    val ms = TimeUnit.NANOSECONDS.toMillis(nanos)
    if (!claimedEvent && ms == 0L) return
    OverheadTimings.add(process, ReturnValueWatcherOverhead, if (claimedEvent) 1 else 0, ms)
    claimedEvent = false
    nanos -= TimeUnit.MILLISECONDS.toNanos(ms)
  }

  private inline fun measure(action: () -> Unit) {
    val start = System.nanoTime()
    try {
      action()
    }
    finally {
      nanos += System.nanoTime() - start
    }
  }
}

/** The row of the feature in the Overhead view. It holds no state, so it cannot keep a debug session alive. */
private object ReturnValueWatcherOverhead : OverheadProducer {
  override fun isEnabled(): Boolean {
    return DebuggerSettings.getInstance().WATCH_RETURN_VALUES
  }

  override fun setEnabled(enabled: Boolean) {
    DebuggerSettings.getInstance().WATCH_RETURN_VALUES = enabled
  }

  override fun computePresentation(): OverheadProducer.Presentation {
    return OverheadProducer.Presentation(JavaDebuggerBundle.message("action.watches.method.return.value.enable"),
                                         AllIcons.Debugger.WatchLastReturnValue)
  }
}
