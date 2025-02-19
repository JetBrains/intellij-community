// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.MethodInvokeUtils.getMethodHandlesImplLookup
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.impl.ThreadDumpItemsProvider
import com.intellij.debugger.impl.ThreadDumpItemsProviderFactory
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.rt.debugger.VirtualThreadDumper
import com.intellij.threadDumpParser.ThreadDumpParser
import com.intellij.threadDumpParser.ThreadState
import com.intellij.unscramble.DumpItem
import com.intellij.unscramble.JavaThreadDumpItem
import com.intellij.util.lang.JavaVersion
import com.jetbrains.jdi.ThreadReferenceImpl
import com.sun.jdi.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.lang.Long
import java.util.concurrent.CancellationException
import kotlin.Int
import kotlin.Pair
import kotlin.String
import kotlin.Throwable
import kotlin.checkNotNull
import kotlin.let
import kotlin.time.Duration.Companion.seconds
import kotlin.to

class ThreadDumpAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      return
    }
    val context = (DebuggerManagerEx.getInstanceEx(project)).context

    val session = context.debuggerSession
    val managerThread = context.managerThread!!
    if (session != null && session.isAttached) {
      executeOnDMT(managerThread) {
        val dumpItems = buildThreadDump(context)
        withContext(Dispatchers.EDT) {
          val xSession = session.xDebugSession
          if (xSession != null) {
            DebuggerUtilsEx.addDumpItems(project, dumpItems, xSession.ui, session.searchScope)
          }
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = e.project
    if (project == null) {
      presentation.setEnabled(false)
      return
    }
    val debuggerSession = DebuggerManagerEx.getInstanceEx(project).context.debuggerSession
    presentation.setEnabled(debuggerSession != null && debuggerSession.isAttached)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  companion object {
    private val extendedProviders: ExtensionPointName<ThreadDumpItemsProviderFactory> =
      ExtensionPointName.Companion.create("com.intellij.debugger.dumpItemsProvider")

    @JvmStatic
    fun buildThreadStates(vmProxy: VirtualMachineProxyImpl): List<ThreadState> {
      return buildThreadStates(vmProxy, emptyList())
    }

    @JvmStatic
    fun renderLocation(location: Location): @NonNls String {
      return "at " + DebuggerUtilsEx.getLocationMethodQName(location) +
             "(" + DebuggerUtilsEx.getSourceName(location, "Unknown Source") + ":" + DebuggerUtilsEx.getLineNumber(location, false) + ")"
    }

    private suspend fun buildThreadDump(context: DebuggerContextImpl): List<DumpItem> {
      fun fallback() =
        buildJavaPlatformThreadDump(context).map(::JavaThreadDumpItem)

      if (!Registry.`is`("debugger.thread.dump.extended")) {
        return fallback()
      }

      return try {
        val providers = extendedProviders.extensionList.map { it.getProvider(context) }

        if (providers.any { it.requiresEvaluation() }) {
          val timeout = Registry.intValue("debugger.thread.dump.suspension.timeout.seconds", 5).seconds
          try {
            suspendAllAndEvaluate(context, timeout) { suspendContext ->
              providers.flatMap { it.getItems(suspendContext) }
            }
          }
          catch (_: TimeoutCancellationException) {
            thisLogger().warn("timeout while waiting for evaluatable context ($timeout)")
            fallback()
          }
        }
        else {
          providers.flatMap { it.getItems(null) }
        }
      }
      catch (e: Throwable) {
        when (e) {
          is CancellationException, is ControlFlowException -> {
            throw e
          }
          else -> {
            thisLogger().error(e)
            fallback()
          }
        }
      }
    }

    fun buildJavaPlatformThreadDump(context: DebuggerContextImpl): List<ThreadState> {
      val vm = context.debugProcess!!.virtualMachineProxy
      vm.suspend()
      try {
        return buildThreadStates(vm)
      }
      finally {
        vm.resume()
      }
    }
  }
}

private fun renderLockedObject(monitor: ObjectReference): String {
  return "locked " + renderObject(monitor)
}

private fun renderObject(monitor: ObjectReference): String {
  var monitorTypeName: String?
  try {
    monitorTypeName = monitor.referenceType().name()
  }
  catch (e: Throwable) {
    monitorTypeName = "Error getting object type: '" + e.message + "'"
  }
  return "<0x" + Long.toHexString(monitor.uniqueID()) + "> (a " + monitorTypeName + ")"
}

private fun threadStatusToJavaThreadState(status: Int): String {
  return when (status) {
    ThreadReference.THREAD_STATUS_MONITOR -> Thread.State.BLOCKED.name
    ThreadReference.THREAD_STATUS_NOT_STARTED -> Thread.State.NEW.name
    ThreadReference.THREAD_STATUS_RUNNING -> Thread.State.RUNNABLE.name
    ThreadReference.THREAD_STATUS_SLEEPING -> Thread.State.TIMED_WAITING.name
    ThreadReference.THREAD_STATUS_WAIT -> Thread.State.WAITING.name
    ThreadReference.THREAD_STATUS_ZOMBIE -> Thread.State.TERMINATED.name
    ThreadReference.THREAD_STATUS_UNKNOWN -> "unknown"
    else -> "undefined"
  }
}

private fun threadStatusToState(status: Int): String {
  return when (status) {
    ThreadReference.THREAD_STATUS_MONITOR -> "waiting for monitor entry"
    ThreadReference.THREAD_STATUS_NOT_STARTED -> "not started"
    ThreadReference.THREAD_STATUS_RUNNING -> "runnable"
    ThreadReference.THREAD_STATUS_SLEEPING -> "sleeping"
    ThreadReference.THREAD_STATUS_WAIT -> "waiting"
    ThreadReference.THREAD_STATUS_ZOMBIE -> "zombie"
    ThreadReference.THREAD_STATUS_UNKNOWN -> "unknown"
    else -> "undefined"
  }
}

private fun threadName(threadReference: ThreadReference): String {
  return threadReference.name() + "@" + threadReference.uniqueID()
}

private fun getThreadField(
  fieldName: String,
  threadType: ReferenceType, threadObj: ThreadReference,
  holderType: ReferenceType?, holderObj: ObjectReference?,
): Value? {
  DebuggerUtils.findField(threadType, fieldName)?.let {
    return threadObj.getValue(it)
  }

  if (holderType != null) {
    checkNotNull(holderObj)
    DebuggerUtils.findField(holderType, fieldName)?.let {
      return holderObj.getValue(it)
    }
  }

  return null
}

private fun buildThreadStates(
  vmProxy: VirtualMachineProxyImpl,
  virtualThreads: List<Pair<ThreadReference, String>>,
): List<ThreadState> {

  // By default it includes only platform threads. Unless JDWP's option includevirtualthreads is enabled.
  // TODO: remove duplicates if includevirtualthreads is enabled.
  val platformThreads = getPlatformThreadsWithStackTraces(vmProxy)

  val allThreads = platformThreads + virtualThreads.asSequence()

  val result = mutableListOf<ThreadState>()
  val nameToThreadMap = mutableMapOf<String, ThreadState>()
  val waitingMap = mutableMapOf<String, String>() // key 'waits_for' value
  for ((threadReference, rawStackTrace) in allThreads) {
    val buffer = StringBuilder()
    val threadStatus = threadReference.status()
    if (threadStatus == ThreadReference.THREAD_STATUS_ZOMBIE) {
      continue
    }
    val threadName = threadName(threadReference)
    val threadState = ThreadState(threadName, threadStatusToState(threadStatus))
    nameToThreadMap[threadName] = threadState
    result += threadState
    threadState.javaThreadState = threadStatusToJavaThreadState(threadStatus)

    buffer.append("\"").append(threadName).append("\"")

    val threadType = threadReference.referenceType()
    if (threadType != null) {
      // Since Project Loom some of Thread's fields are encapsulated into FieldHolder,
      // so we try to look up fields in the thread itself and in its holder.
      val (holderObj, holderType) = when (val value = getThreadField("holder", threadType, threadReference, null, null)) {
        is ObjectReference -> value to value.referenceType()
        else -> null to null
      }

      when (val value = getThreadField("daemon", threadType, threadReference, holderType, holderObj)) {
        is BooleanValue ->
          if (value.booleanValue()) {
            buffer.append(" daemon")
            threadState.isDaemon = true
          }
      }

      when (val value = getThreadField("priority", threadType, threadReference, holderType, holderObj)) {
        is IntegerValue ->
          buffer.append(" prio=").append(value.intValue())
      }

      when (val value = getThreadField("tid", threadType, threadReference, holderType, holderObj)) {
        is LongValue -> {
          buffer.append(" tid=0x").append(Long.toHexString(value.longValue()))
          buffer.append(" nid=NA")
        }
      }
    }

    if (threadReference is ThreadReferenceImpl && threadReference.isVirtual()) {
      buffer.append(" virtual")
      threadState.isVirtual = true
    }

    //ThreadGroupReference groupReference = threadReference.threadGroup();
    //if (groupReference != null) {
    //  buffer.append(", ").append(JavaDebuggerBundle.message("threads.export.attribute.label.group", groupReference.name()));
    //}
    val state = threadState.state
    if (state != null) {
      buffer.append(" ").append(state)
    }

    buffer.append("\n  java.lang.Thread.State: ").append(threadState.javaThreadState)

    try {
      if (vmProxy.canGetOwnedMonitorInfo() && vmProxy.canGetMonitorInfo()) {
        val list = threadReference.ownedMonitors()
        for (reference in list) {
          if (!vmProxy.canGetMonitorFrameInfo()) { // java 5 and earlier
            buffer.append("\n\t ").append(renderLockedObject(reference))
          }
          val waiting = reference.waitingThreads()
          for (thread in waiting) {
            val waitingThreadName = threadName(thread)
            waitingMap[waitingThreadName] = threadName
            buffer.append("\n\t blocks ").append(waitingThreadName)
          }
        }
      }

      val waitedMonitor = if (vmProxy.canGetCurrentContendedMonitor()) threadReference.currentContendedMonitor() else null
      if (waitedMonitor != null) {
        if (vmProxy.canGetMonitorInfo()) {
          val waitedMonitorOwner = waitedMonitor.owningThread()
          if (waitedMonitorOwner != null) {
            val monitorOwningThreadName = threadName(waitedMonitorOwner)
            waitingMap[threadName] = monitorOwningThreadName
            buffer.append("\n\t waiting for ").append(monitorOwningThreadName)
              .append(" to release lock on ").append(waitedMonitor)
          }
        }
      }

      val lockedAt = mutableMapOf<Int, MutableList<ObjectReference>>()
      if (vmProxy.canGetMonitorFrameInfo()) {
        for (m in threadReference.ownedMonitorsAndFrames()) {
          if (m is MonitorInfo) { // see JRE-937
            val monitors = lockedAt.getOrPut(m.stackDepth()) { mutableListOf() }
            monitors += m.monitor()
          }
        }
      }

      if (lockedAt.isEmpty()) {
        buffer.append('\n').append(rawStackTrace)
      }
      else {
        val lines = rawStackTrace.lines()
        lines.forEachIndexed { index, line ->
          buffer.append('\n').append(line)
          lockedAt.remove(index)?.forEach { monitor ->
            buffer.append("\n\t  - ").append(renderLockedObject(monitor))
          }
        }

        // Dump remaining monitors in case of corrupted stack trace.
        for (monitors in lockedAt.values) {
          for (monitor in monitors) {
            buffer.append("\n\t  - ").append(renderLockedObject(monitor))
          }
        }
      }
    }
    catch (_: IncompatibleThreadStateException) {
      buffer.append("\n\t Incompatible thread state: thread not suspended")
    }
    val hasEmptyStack = rawStackTrace.isEmpty()
    threadState.setStackTrace(buffer.toString(), hasEmptyStack)
    ThreadDumpParser.inferThreadStateDetail(threadState)
  }

  for ((waiting, awaited) in waitingMap) {
    val waitingThread = nameToThreadMap[waiting] ?: continue // continue if zombie
    val awaitedThread = nameToThreadMap[awaited] ?: continue // continue if zombie
    awaitedThread.addWaitingThread(waitingThread)
  }

  // detect simple deadlocks
  for (thread in result) {
    for (awaitingThread in thread.awaitingThreads) {
      if (awaitingThread.isAwaitedBy(thread)) {
        thread.addDeadlockedThread(awaitingThread)
        awaitingThread.addDeadlockedThread(thread)
      }
    }
  }

  ThreadDumpParser.sortThreads(result)
  return result
}

private fun getPlatformThreadsWithStackTraces(vmProxy: VirtualMachineProxyImpl): Sequence<Pair<ThreadReference, String>> {
  return vmProxy.virtualMachine.allThreads().asSequence().map { threadReference ->
    val frames =
      try {
        threadReference.frames()
      }
      catch (_: IncompatibleThreadStateException) {
        return@map threadReference to "Incompatible thread state: thread not suspended"
      }

    threadReference to buildString {
      for (stackFrame in frames) {
        if (this.isNotEmpty()) {
          append('\n')
        }
        append("\t  ")
        try {
          append(ThreadDumpAction.renderLocation(stackFrame.location()))
        }
        catch (e: InvalidStackFrameException) {
          append("Invalid stack frame: ").append(e.message)
        }
      }
    }
  }
}

private class JavaThreadsProvider : ThreadDumpItemsProviderFactory() {
  override fun getProvider(context: DebuggerContextImpl) = object : ThreadDumpItemsProvider {
    val vm = context.debugProcess!!.virtualMachineProxy

    val dumpVirtualThreads =
      Registry.`is`("debugger.thread.dump.include.virtual.threads") &&
      // Virtual threads first appeared in Java 19 as part of Project Loom.
      JavaVersion.parse(vm.version()).feature >= 19 &&
      // Check if VirtualThread class is at least loaded.
      vm.classesByName("java.lang.VirtualThread").isNotEmpty()

    override fun requiresEvaluation() = dumpVirtualThreads

    override fun getItems(suspendContext: SuspendContextImpl?): List<DumpItem> {
      val virtualThreads =
        if (dumpVirtualThreads) evaluateAndGetAllVirtualThreads(suspendContext!!)
        else emptyList()

      return buildThreadStates(vm, virtualThreads)
        .map(::JavaThreadDumpItem)
    }

    private fun evaluateAndGetAllVirtualThreads(suspendContext: SuspendContextImpl): List<Pair<ThreadReference, String>> {
      val evaluationContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)

      val lookupImpl = getMethodHandlesImplLookup(evaluationContext)
      if (lookupImpl == null) {
        thisLogger().error("Cannot get MethodHandles.Lookup.IMPL_LOOKUP")
        return emptyList()
      }

      val evaluated = try {
        DebuggerUtilsImpl.invokeHelperMethod(
          evaluationContext,
          VirtualThreadDumper::class.java, "getAllVirtualThreadsWithStackTraces",
          listOf(lookupImpl)
        )
      }
      catch (e: EvaluateException) {
        thisLogger().error(e)
        return emptyList()
      }
      val packedThreadsAndStackTraces = (evaluated as ArrayReference?)?.values ?: emptyList()

      return buildList {
        var i = 0
        while (i < packedThreadsAndStackTraces.size) {
          val stackTrace = (packedThreadsAndStackTraces[i++] as StringReference).value()
          while (true) {
            val thread = packedThreadsAndStackTraces[i++]
            if (thread == null) {
              break
            }
            add(thread as ThreadReference to stackTrace)
          }
        }
      }
    }
  }
}