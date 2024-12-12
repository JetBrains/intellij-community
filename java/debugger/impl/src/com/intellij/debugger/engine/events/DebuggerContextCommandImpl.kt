// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.events

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.SuspendManagerUtil
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.openapi.diagnostic.Logger
import com.sun.jdi.ObjectCollectedException
import com.sun.jdi.ThreadReference
import com.sun.jdi.request.EventRequest
import org.jetbrains.annotations.ApiStatus

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

  private var myCustomSuspendContext: SuspendContextImpl? = null

  override val suspendContext: SuspendContextImpl?
    get() {
      if (myCustomSuspendContext != null) return myCustomSuspendContext
      return run {
        val context = super.suspendContext
        when {
          customThread == null -> context
          context == null || context.isResumed || !context.suspends(customThread) -> {
            SuspendManagerUtil.findContextByThread(debuggerContext.debugProcess!!.suspendManager, customThread)
          }
          else -> context
        }
      }.also {
        myCustomSuspendContext = it
      }
    }

  private val thread: ThreadReferenceProxyImpl?
    get() = customThread ?: debuggerContext.threadProxy

  final override suspend fun contextActionSuspend(suspendContext: SuspendContextImpl) {
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
      threadActionSuspend(suspendContext)
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

  open fun threadAction(suspendContext: SuspendContextImpl): Unit = throw AbstractMethodError()

  @ApiStatus.Experimental
  open suspend fun threadActionSuspend(suspendContext: SuspendContextImpl): Unit = threadAction(suspendContext)

  companion object {
    private val LOG = Logger.getInstance(DebuggerContextCommandImpl::class.java)
  }
}
