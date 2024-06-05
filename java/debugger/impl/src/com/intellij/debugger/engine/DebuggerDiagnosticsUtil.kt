// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.containers.toArray
import com.sun.jdi.request.EventRequest

private enum class ThreadModelState {
  /** The thread is running and was not stopped (or considered as stopped) in any context */
  RUNNING,

  /**
   * The thread has been stopped by at least one suspend-all context but then explicitly resumed by user.
   * Note that the thread may be temporarily stopped by some breakpoint.
   * But no paused context should be existed for EXPLICITLY_RESUMED thread.
   * Explicitly resumed threads may exist in the debugger model only iff there is at least on suspend-all context.
   */
  EXPLICITLY_RESUMED,

  /**
   * The thread has been stopped by at least one suspend context (suspend-all or suspend thread) and was not explicitly resumed after it.
   * Actually, the thread may be resumed by suspend-all invocation watcher.
   * But this resume/suspend activity is the responsibility of the watcher only.
   * So, in the debugger model, the thread would be seen as STOPPED by some context.
   */
  STOPPED,

  /**
   * The thread has been stopped by some context and is used to perform some invocation.
   * After the invocation, the thread should be returned to STOPPED state or to the EXPLICITLY_RESUMED state
   * (iff it was resumed by user).
   */
  EVALUATING,
}

object DebuggerDiagnosticsUtil {
  @JvmStatic
  val recursionTracker = ThreadLocal<Boolean>()

  @JvmStatic
  fun checkThreadsConsistency(process: DebugProcessImpl, reportDiffWithRealCounter: Boolean) {
    val suspendManager = process.suspendManager
    val invocationWatching = process.myThreadBlockedMonitor.myInvocationWatching
    // Anyway, model problems can be detected only for threads the engine already worked with
    @Suppress("TestOnlyProblems")
    val allThreads = process.virtualMachineProxy.evenDirtyAllThreads

    val allContexts = suspendManager.eventContexts

    val suspendAllContexts = allContexts.filter { it.suspendPolicy == EventRequest.SUSPEND_ALL }

    val problems = mutableListOf<String>()

    for (threadProxy in allThreads) {
      val suspendingContexts = SuspendManagerUtil.getSuspendingContexts(suspendManager, threadProxy)
      val resumedByWatching = if (invocationWatching != null && suspendingContexts.contains(invocationWatching.mySuspendAllContext)) 1
      else 0
      val threadModelSuspendCount = threadProxy.wholeSuspendModelNumber
      if (suspendingContexts.size - resumedByWatching != threadModelSuspendCount) {
        val s = if (invocationWatching != null) ("RBW=$resumedByWatching, ") else ""
        problems += "Error in model for " + threadProxy + ": " + s + "model count = " + threadModelSuspendCount +
                    ", suspending contexts: " + suspendingContexts
      }
      if (reportDiffWithRealCounter) {
        val realSuspendCount = threadProxy.suspendCount
        if (threadModelSuspendCount != realSuspendCount) {
          problems += "Error in model for " + threadProxy + ": model count = " + threadModelSuspendCount +
                      ", real count = " + realSuspendCount
        }
      }

      val state = getThreadState(threadProxy)

      when (state) {
        ThreadModelState.EVALUATING -> {
          if (suspendingContexts.isNotEmpty()) {
            problems += "Thread $threadProxy is considered as evaluating, but has suspending contexts: $suspendingContexts"
          }
        }
        ThreadModelState.EXPLICITLY_RESUMED -> {
          val pausedSuspendingContexts = suspendManager.pausedContexts.filter { it.suspends(threadProxy) }
          if (pausedSuspendingContexts.isNotEmpty()) {
            problems +=
              "Thread $threadProxy is considered as resumed explicitly, but has paused suspending contexts: $pausedSuspendingContexts"
          }
          if (suspendAllContexts.isEmpty()) {
            problems += "Thread $threadProxy is considered as resumed explicitly, but no one suspend-all context is found"
          }
        }
        ThreadModelState.RUNNING -> {
          if (suspendAllContexts.isNotEmpty()) {
            problems += "Thread $threadProxy is considered as running, but there are suspend-all contexts: $suspendAllContexts"
          }
          val suspendThreadContexts = allContexts.filter {
            it.suspendPolicy == EventRequest.SUSPEND_EVENT_THREAD && it.eventThread == threadProxy
          }
          if (suspendThreadContexts.isNotEmpty()) {
            problems += "Thread $threadProxy is considered as running, but there are suspend-thread contexts for it: $suspendAllContexts"
          }
        }
        else -> {}
      }
    }

    for (context in allContexts) {
      if (context.suspendPolicy == EventRequest.SUSPEND_ALL) {
        val resumedThreads = context.myResumedThreads
        if (resumedThreads != null && resumedThreads.isNotEmpty()) {
          for (threadProxy in resumedThreads) {
            val state = getThreadState(threadProxy)
            if (state != ThreadModelState.EVALUATING && state != ThreadModelState.EXPLICITLY_RESUMED) {
              problems += "Invalid state of thread $threadProxy: $state"
            }
          }
        }
      }
      if (context.suspendPolicy == EventRequest.SUSPEND_EVENT_THREAD) {
        val resumedThreads = context.myResumedThreads
        if (resumedThreads != null && resumedThreads.isNotEmpty()) {
          problems += "Suspend thread context with explicitly resumed threads: $resumedThreads"
        }
      }

      if (context.isEvaluating) {
        val evaluationContext = checkNotNull(context.evaluationContext)
        val threadForEvaluation = evaluationContext.threadForEvaluation
        if (threadForEvaluation != null) {
          if (!threadForEvaluation.isEvaluating) {
            problems += "In $context found $evaluationContext with $threadForEvaluation as thread but it is not evaluating"
          }
        }
        else {
          problems += "No thread for evaluation for evaluating $context"
        }
      }
    }

    if (problems.isNotEmpty()) {
      logError(process, "Found ${problems.size} problems", attachment = Attachment("Problems", problems.joinToString(separator = "\n")))
    }
  }

  @JvmStatic
  private fun getThreadState(thread: ThreadReferenceProxyImpl): ThreadModelState {
    val suspendManager =
      (thread.virtualMachineProxy.debugProcess as DebugProcessImpl).suspendManager as SuspendManagerImpl
    if (thread.isEvaluating) {
      return ThreadModelState.EVALUATING
    }

    if (suspendManager.myExplicitlyResumedThreads.contains(thread)) {
      return ThreadModelState.EXPLICITLY_RESUMED
    }

    if (suspendManager.eventContexts.any { it.suspendPolicy == EventRequest.SUSPEND_ALL || it.eventThread == thread }) {
      return ThreadModelState.STOPPED
    }

    return ThreadModelState.RUNNING
  }

  @JvmStatic
  fun assertTrue(process: DebugProcessImpl, value: Boolean, text: () -> String) {
    if (value) return
    logError(process, "Assertion failed: " + text())
  }

  @JvmStatic
  @JvmOverloads
  fun logError(process: DebugProcessImpl, message: String, e: Throwable? = null, attachment: Attachment? = null) {
    val paramAttachment = if (attachment != null) listOf(attachment) else emptyList()
    val attachments = (paramAttachment + createStateAttachments(process)).toArray(Attachment.EMPTY_ARRAY)
    if (e == null) {
      thisLogger().error(message, *attachments)
    }
    else {
      thisLogger().error(message, e, *attachments)
    }
  }

  @JvmStatic
  private fun createStateAttachments(process: DebugProcessImpl) : List<Attachment> {
    if (recursionTracker.get() == true) {
      return listOf(Attachment("Recursion problem detected, just thread dump", noErr { ThreadDumper.dumpThreadsToString() }))
    }
    else {
      try {
        recursionTracker.set(true)
        return listOf(getDebuggerStateOverview(process),
                      createThreadsAttachment(process),
                      Attachment("VM thread dump", noErr { ThreadDumper.dumpThreadsToString() })) +
               process.suspendManager.eventContexts.map { it.toAttachment() }
      }
      finally {
        recursionTracker.remove()
      }
    }
  }

  @JvmStatic
  private fun createThreadsAttachment(process: DebugProcessImpl): Attachment {
    val virtualMachine = process.virtualMachineProxy
    val threads = virtualMachine.allThreads().joinToString(separator = "\n") {
      "(${noErr { getThreadState(it) }}) $it, model suspend counter = ${it.wholeSuspendModelNumber}, real suspend counter = ${it.suspendCount}"
    }
    val vmModelCount = "VM suspend model count = ${virtualMachine.modelSuspendCount}"
    val blockedThreadsInfo = if (process.myThreadBlockedMonitor.isInResumeAllMode) {
      if (ThreadBlockedMonitor.isNewSuspendAllInvocationWatcher()) {
        val invocationWatching = process.myThreadBlockedMonitor.myInvocationWatching
        if (invocationWatching != null) "Watcher for ${noErr { invocationWatching.mySuspendAllContext }} is active"
        else "some problem with watcher"
      }
      else {
        "old invocation watcher is using"
      }
    } else "no suspend-all invocation watcher is activated"
    return Attachment("Threads state", "$vmModelCount\n$blockedThreadsInfo\n$threads")
  }

  @JvmStatic
  private fun getDebuggerStateOverview(process: DebugProcessImpl): Attachment {
    val suspendManager = process.suspendManager
    val currentCommand = DebuggerManagerThreadImpl.getCurrentCommand()
    val currentCommandText = "Current command = $currentCommand\n"
    val currentSuspendContext = (currentCommand as? SuspendContextCommandImpl)?.suspendContext
    val currentSuspendContextText = "Current Command Suspend context = $currentSuspendContext\n"
    val registryInfo = Registry.getAll()
      .filter { it.key.startsWith("debugger.") && it.isChangedFromDefault }
      .joinToString(separator = "") { "${it.key} = ${it.asString()}\n" }
    val content = registryInfo +
                  currentCommandText +
                  currentSuspendContextText +
                  noErr { process.stateForDiagnostics } +
                  noErr { (suspendManager as SuspendManagerImpl).stateForDiagnostics }
    return Attachment("Debugger state overview", content)
  }

  @JvmStatic
  private fun noErr(f: () -> Any?): String {
    try {
      return f().toString()
    }
    catch (e: Exception) {
      return e.toString()
    }
  }

  @JvmStatic
  fun logDebug(message: String) {
    thisLogger().debug(message)
  }
}
