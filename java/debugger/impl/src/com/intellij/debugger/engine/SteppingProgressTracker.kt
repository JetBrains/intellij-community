// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.sun.jdi.request.EventRequest
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.annotations.Nls
import java.util.concurrent.CompletableFuture

private data class TrackedSteppingData(val stepCompetedStatus: CompletableDeferred<Unit>, val threadFilter: (ThreadReferenceProxy?) -> Boolean)

private class SteppingProgressTracker(private val debuggerProcessImpl: DebugProcessImpl) {
  private val trackedStepping = mutableListOf<TrackedSteppingData>()

  fun installListeners() {
    debuggerProcessImpl.addDebugProcessListener(object : DebugProcessListener {
      override fun paused(suspendContext: SuspendContext) {
        val thread = suspendContext.thread
        val completedSteps = trackedStepping.filter { it.threadFilter(thread) }
        for ((stepCompetedStatus, _) in completedSteps) {
            stepCompetedStatus.complete(Unit)
        }
        trackedStepping.removeAll(completedSteps)
      }
    })
  }

  fun addStepping(stepCompetedStatus: CompletableDeferred<Unit>, threadFilter: (ThreadReferenceProxy?) -> Boolean) {
    trackedStepping.add(TrackedSteppingData(stepCompetedStatus, threadFilter))
  }
}

private class CancelingSteppingListener : SteppingListener {
  override fun beforeSteppingStarted(suspendContext: SuspendContextImpl, steppingAction: SteppingAction) {
    val debuggerProcessImpl: DebugProcessImpl = suspendContext.debugProcess
    val filter: LightOrRealThreadInfo? = debuggerProcessImpl.requestsManager.filterThread

    val threadForStepping: ThreadReferenceProxyImpl? =
      if (filter != null) suspendContext.virtualMachineProxy.getThreadReferenceProxy(filter.realThread)
      else suspendContext.thread

    val needSuspendOnlyThread = suspendContext.suspendPolicy == EventRequest.SUSPEND_EVENT_THREAD && threadForStepping != null

    val steppingName = steppingAction.steppingName
    val stepCompetedStatus = CompletableDeferred<Unit>()

    val managerThread = suspendContext.managerThread
    managerThread.schedule(PrioritizedTask.Priority.NORMAL) {
      // Need to schedule in the separate debugger command, so async name getter will not fail
      // because of the current suspend context become resumed
      val whereStrFuture: CompletableFuture<String> = if (threadForStepping != null) {
        DebuggerUtilsAsync.nameAsync(threadForStepping.threadReference).thenApply { name ->
          JavaDebuggerBundle.message("stepping.filter.real.thread.name", name)
        }
      }
      else {
        CompletableFuture<String>.completedFuture(filter?.filterName ?: debuggerProcessImpl.session.sessionName)
      }

      whereStrFuture.thenAccept { whereStr ->
        @Suppress("HardCodedStringLiteral")
        val steppingRestrictionMessage = getSteppingRestrictionMessage(whereStr, steppingAction)
        managerThread.makeCancelable(debuggerProcessImpl.project, steppingRestrictionMessage, steppingName, stepCompetedStatus) {
          val command: DebuggerCommandImpl =
            if (needSuspendOnlyThread) debuggerProcessImpl.createFreezeThreadCommand(threadForStepping)
            else debuggerProcessImpl.createPauseCommand(threadForStepping)
          managerThread.schedule(command)
        }
      }
    }

    val tracker = suspendContext.debugProcess.mySteppingProgressTracker
    tracker.addStepping(stepCompetedStatus) { thread ->
      !needSuspendOnlyThread || thread == null || thread == threadForStepping
    }
  }
}

private val SteppingAction.steppingName: @Nls String get() = when (this) {
  SteppingAction.STEP_INTO -> JavaDebuggerBundle.message("status.step.into")
  SteppingAction.STEP_OUT -> JavaDebuggerBundle.message("status.step.out")
  SteppingAction.STEP_OVER -> JavaDebuggerBundle.message("status.step.over")
  SteppingAction.RUN_TO_CURSOR -> JavaDebuggerBundle.message("status.run.to.cursor")
}

private fun getSteppingRestrictionMessage(whereSteppingIsPerformed: @Nls String?, steppingAction: SteppingAction): @Nls String {
  return when (steppingAction) {
    SteppingAction.RUN_TO_CURSOR -> JavaDebuggerBundle.message("status.run.to.cursor.in", whereSteppingIsPerformed)
    else -> JavaDebuggerBundle.message("status.stepping.in", whereSteppingIsPerformed)
  }
}
