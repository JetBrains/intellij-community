// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val hitRunToCursorTimeout get() = Registry.intValue("debugger.run.to.cursor.any.hit.timeout.ms", 1000)
private val waitingThreadsLimit get() = Registry.intValue("debugger.run.to.cursor.waited.threads.limit", 1)

/**
 * This manager is responsible for pausing run-to-cursor stepping in non-stepping thread if there were not too many hits and the stepping
 * thread was not hit during [hitRunToCursorTimeout] timeout.
 */
internal class RunToCursorManager(debugProcessImpl: DebugProcessImpl, val coroutineScope: CoroutineScope) {
  private val waitingContexts: MutableList<SuspendContextImpl> = mutableListOf()

  // Just for logging the warning
  private var logWarningJob: Job? = null

  private var waitingJob: Job? = null

  private var isTryingToPauseAnotherHitEnabled = false

  init {
    debugProcessImpl.addDebugProcessListener(object : DebugProcessListener {
      override fun paused(suspendContext: SuspendContext) {
        logWarningJob?.cancel()
        logWarningJob = null

        // return to the default state
        resumeWaitingThreadsAndCancelWaitingJob()
      }
    })
  }

  fun onRunToCursorCommandStarted() {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    isTryingToPauseAnotherHitEnabled = true
    // Just in case
    resumeWaitingThreadsAndCancelWaitingJob()
  }

  fun shouldTryToPauseAnotherHit(suspendContext: SuspendContextImpl): Boolean {
    DebuggerManagerThreadImpl.assertIsManagerThread()

    if (!isTryingToPauseAnotherHitEnabled) {
      return false
    }

    dropAlreadyResumedContexts()
    if (waitingContexts.size >= waitingThreadsLimit) {
      val eventSet = suspendContext.eventSet
      logWarningJob = coroutineScope.launch {
        // Just use the same timeout to wait, maybe we will pause in the stepping thread
        delay(hitRunToCursorTimeout.toLong())
        thisLogger().warn("Too many Run-to-Cursor hits from other threads, $eventSet")
      }

      isTryingToPauseAnotherHitEnabled = false
      resumeWaitingThreadsAndCancelWaitingJob()
      return false
    }
    return true
  }

  fun saveRunToCursorHit(suspendContext: SuspendContextImpl) {
    DebuggerManagerThreadImpl.assertIsManagerThread()

    if (!isTryingToPauseAnotherHitEnabled) {
      // This context normally should have been resumed by thread filter, so just resume it here
      // This situation happens because of asynchronous evaluation on different threads
      suspendContext.debugProcess.suspendManager.voteResume(suspendContext)
      return
    }
    waitingContexts.add(suspendContext)

    if (waitingJob != null) {
      return
    }
    startRunToCursorTracking(suspendContext.managerThread)
  }

  private fun startRunToCursorTracking(managerThread: DebuggerManagerThreadImpl) {
    waitingJob = executeOnDMT(managerThread) {
      delay(hitRunToCursorTimeout.toLong())

      dropAlreadyResumedContexts()

      if (waitingContexts.isEmpty()) {
        waitingJob = null
        return@executeOnDMT
      }
      val whereToStop = waitingContexts.firstOrNull() ?: return@executeOnDMT
      waitingContexts.remove(whereToStop)

      XDebuggerManagerImpl.getNotificationGroup()
        .createNotification(JavaDebuggerBundle.message("message.run.to.cursor.paused.in.another.thread"), MessageType.WARNING)
        .notify(whereToStop.debugProcess.project)

      whereToStop.debugProcess.suspendManager.voteSuspend(whereToStop)

      // Other hits will be resumed in the pause listener declared above, in the constructor of this manager
    }
  }

  private fun resumeWaitingThreadsAndCancelWaitingJob() {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    waitingJob?.cancel()
    waitingJob = null
    dropAlreadyResumedContexts()
    for (context in waitingContexts) {
      context.debugProcess.suspendManager.voteResume(context)
    }
    waitingContexts.clear()
  }

  private fun dropAlreadyResumedContexts() {
    waitingContexts.removeAll { it.isResumed }
  }
}
