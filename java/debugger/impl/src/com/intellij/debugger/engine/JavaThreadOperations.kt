// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.openapi.ui.MessageType
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object JavaThreadOperations {
  fun resumeThread(thread: ThreadReferenceProxyImpl, debugProcess: DebugProcessImpl, managerThread: DebuggerManagerThreadImpl) {
    executeOnDMT(managerThread) {
      val suspendingContext = SuspendManagerUtil.getSuspendingContext(debugProcess.suspendManager, thread)
      if (suspendingContext != null) {
        managerThread.invokeNow(debugProcess.createResumeThreadCommand(suspendingContext, thread))
      }
    }
  }

  fun freezeThread(thread: ThreadReferenceProxyImpl, debugProcess: DebugProcessImpl, managerThread: DebuggerManagerThreadImpl) {
    executeOnDMT(managerThread) {
      managerThread.invokeNow(debugProcess.createFreezeThreadCommand(thread))
    }
  }

  fun interruptThread(thread: ThreadReferenceProxyImpl, debugProcess: DebugProcessImpl, managerThread: DebuggerManagerThreadImpl) {
    executeOnDMT(managerThread) {
      try {
        thread.threadReference.interrupt()
      }
      catch (_: UnsupportedOperationException) {
        XDebuggerManagerImpl.getNotificationGroup()
          .createNotification(JavaDebuggerBundle.message("thread.operation.interrupt.is.not.supported.by.vm"), MessageType.INFO)
          .notify(debugProcess.project)
      }
    }
  }
}
