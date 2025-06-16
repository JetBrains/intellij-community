// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.events

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.SuspendManagerUtil
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.openapi.diagnostic.Logger
import com.sun.jdi.ObjectCollectedException
import com.sun.jdi.ThreadReference
import com.sun.jdi.request.EventRequest

/**
 *  This command tries to find the [com.intellij.debugger.engine.SuspendContext] suitable for execution,
 *  or cancels if it is not found.
 *
 * @param customThread thread to perform command in
 */
abstract class DebuggerContextCommandImpl @JvmOverloads protected constructor(
  val debuggerContext: DebuggerContextImpl,
  private val customThread: ThreadReferenceProxyImpl? = null,
) : SuspendContextCommandImpl(debuggerContext.suspendContext) {

  override val suspendContext: SuspendContextImpl? by lazy {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    val context = super.suspendContext
    when {
      customThread == null -> context
      context == null || context.isResumed || !context.suspends(customThread) -> {
        // first check paused contexts
        SuspendManagerUtil.getPausedSuspendingContext(debuggerContext.debugProcess!!.suspendManager, customThread)
        ?: SuspendManagerUtil.findContextByThread(debuggerContext.debugProcess!!.suspendManager, customThread)
      }
      else -> context
    }
  }

  private val thread: ThreadReferenceProxyImpl?
    get() = customThread ?: debuggerContext.threadProxy

  final override fun contextAction(suspendContext: SuspendContextImpl) {
    val suspendManager = suspendContext.debugProcess.suspendManager
    val thread = this.thread

    val isSuspended = if (thread == null) {
      suspendContext.suspendPolicy == EventRequest.SUSPEND_ALL
    }
    else {
      try {
        suspendManager.isSuspended(thread)
      }
      catch (_: ObjectCollectedException) {
        notifyCancelled()
        return
      }
    }

    if (isSuspended) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Context thread " + suspendContext.getThread())
        LOG.debug("Debug thread $thread")
      }
      threadAction(suspendContext)
    }
    else {
      // no suspend context currently available
      val suspendContextForThread = if (customThread != null) suspendContext else SuspendManagerUtil.findContextByThread(suspendManager, thread)
      if (suspendContextForThread != null && (thread == null || thread.status() != ThreadReference.THREAD_STATUS_ZOMBIE)) {
        suspendContextForThread.postponeCommand(this)
      }
      else {
        notifyCancelled()
      }
    }
  }

  abstract fun threadAction(suspendContext: SuspendContextImpl)

  companion object {
    private val LOG = Logger.getInstance(DebuggerContextCommandImpl::class.java)
  }
}
