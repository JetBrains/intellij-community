// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.sun.jdi.ThreadReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A replacement for [ThreadBlockedMonitor] to simplify logic */
internal object ThreadSteppingMonitor {
  @JvmStatic
  fun startTrackThreadStepping(thread: ThreadReferenceProxyImpl, suspendContext: SuspendContextImpl) {
    val threadReference = thread.threadReference
    suspendContext.coroutineScope.launch {
      while (true) {
        delay(Registry.intValue("debugger.stepping.current.thread.check.timeout.ms", 100).toLong())
        val fullResumeWasDone = withDebugContext(suspendContext) {
          if (threadReference.status() != ThreadReference.THREAD_STATUS_RUNNING && !suspendContext.isResumed) {
            XDebuggerManagerImpl.getNotificationGroup()
              .createNotification(JavaDebuggerBundle.message("message.resumed.other.threads.while.stepping", threadReference.name()), MessageType.WARNING)
              .notify(suspendContext.debugProcess.project)
            suspendContext.debugProcess.suspendManager.resume(suspendContext)

            true
          } else {
            false
          }
        }
        if (fullResumeWasDone) break
      }
    }
  }
}
