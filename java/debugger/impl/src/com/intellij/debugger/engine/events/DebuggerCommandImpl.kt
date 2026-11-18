// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.events

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.impl.DebuggerTaskImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.openapi.application.ModalityState
import org.jetbrains.annotations.ApiStatus

abstract class DebuggerCommandImpl(override val priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW) : DebuggerTaskImpl() {
  private var myThread: DebuggerManagerThreadImpl? = null
  protected val commandManagerThread: DebuggerManagerThreadImpl
    get() = myThread ?: error("DebuggerManagerThread is not set")

  internal fun setCommandManagerThread(value: DebuggerManagerThreadImpl) {
    if (myThread != null && myThread !== value) {
      error("DebuggerManagerThread is already set")
    }
    myThread = value
  }

  /**
   * The modality of the modal progress that waits for this command, see [DebuggerManagerThreadImpl.startCommandWithModalProgress].
   * While the command runs, [ModalityState.defaultModalityState] returns this modality.
   * Thus, the command can wait for the EDT while the progress dialog is shown.
   */
  @ApiStatus.Internal
  var modalityState: ModalityState? = null
    internal set

  @Throws(Exception::class)
  protected abstract fun action()

  protected open fun commandCancelled() {
  }

  @ApiStatus.Internal
  fun notifyCancelled() {
    try {
      commandCancelled()
    }
    finally {
      release()
    }
  }

  internal fun invokeCommand() {
    try {
      action()
    }
    finally {
      release()
    }
  }
}
