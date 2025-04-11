// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.MethodInvokeUtils.getMethodHandlesImplLookup
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.suspendAllAndEvaluate
import com.intellij.debugger.impl.*
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.rt.debugger.VirtualThreadDumper
import com.intellij.threadDumpParser.ThreadDumpParser
import com.intellij.threadDumpParser.ThreadState
import com.intellij.unscramble.MergeableDumpItem
import com.intellij.unscramble.toDumpItems
import com.intellij.util.lang.JavaVersion
import com.jetbrains.jdi.ThreadReferenceImpl
import com.sun.jdi.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import java.lang.Long as JLong

class ThreadDumpAction {
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

    @ApiStatus.Internal
    suspend fun buildThreadDump(context: DebuggerContextImpl, dumpItemsChannel: SendChannel<List<MergeableDumpItem>>) {

      suspend fun fallback() =
        dumpItemsChannel.send(
          buildJavaPlatformThreadDump(context).toDumpItems()
        )

      if (!Registry.`is`("debugger.thread.dump.extended")) {
        fallback()
        return
      }
      try {
        val providers = extendedProviders.extensionList.map { it.getProvider(context) }

        suspend fun getAllItems(suspendContext: SuspendContextImpl?) {
          coroutineScope {
            // Compute parts of the dump asynchronously
            providers.map { p ->
              launch {
                withBackgroundProgress(context.project, p.progressText) {
                  try {
                    val items = p.getItems(suspendContext)
                    dumpItemsChannel.send(items)
                  }
                  catch (e: CancellationException) {
                    thisLogger().debug("${p.progressText} was cancelled by user.")
                    throw e
                  }
                }
              }
            }
          }
        }

        if (providers.any { it.requiresEvaluation }) {
          val timeout = Registry.intValue("debugger.thread.dump.suspension.timeout.ms", 500).milliseconds
          try {
            suspendAllAndEvaluate(context, timeout) { suspendContext ->
              getAllItems(suspendContext)
            }
          }
          catch (_: TimeoutCancellationException) {
            thisLogger().warn("timeout while waiting for evaluatable context ($timeout)")
            fallback()
          }
        }
        else {
          val vm = context.debugProcess!!.virtualMachineProxy
          vm.suspend()
          try {
            getAllItems(null)
          }
          finally {
            vm.resume()
          }
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
  return "<0x" + JLong.toHexString(monitor.uniqueID()) + "> (a " + monitorTypeName + ")"
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

private fun javaThreadStateToState(javaThreadState: String): String {
  return when (javaThreadState) {
    Thread.State.BLOCKED.name -> "waiting for monitor entry"
    Thread.State.NEW.name -> "not started"
    Thread.State.RUNNABLE.name -> "runnable"
    Thread.State.TIMED_WAITING.name -> "sleeping"
    Thread.State.WAITING.name -> "waiting"
    Thread.State.TERMINATED.name -> "zombie"
    else -> "undefined"
  }
}

private fun threadName(threadReference: ThreadReference): String =
  threadName(threadReference.name(), threadReference)

private fun threadName(threadNameRaw: String, threadReference: ObjectReference): String =
  threadNameRaw + "@" + threadReference.uniqueID()

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
  virtualThreads: List<Triple<ThreadReference, String, Long>>,
): List<ThreadState> {

  val result = mutableListOf<ThreadState>()
  val nameToThreadMap = mutableMapOf<String, ThreadState>()
  val waitingMap = mutableMapOf<String, String>() // key 'waits_for' value

  fun processOne(threadReference: ThreadReference, virtualThreadInfo: Pair<String, Long>?) {
    ProgressManager.checkCanceled()

    val threadName: String
    val stateString: String
    val javaThreadStateString: String
    val isVirtual: Boolean
    val isDaemon: Boolean
    val tid: Long?
    val prio: Int?
    val rawStackTrace: String
    if (virtualThreadInfo != null) {
      val nameStateAndStackTrace = splitFirstTwoAndRemainingLines(virtualThreadInfo.first)
      val nameRaw = nameStateAndStackTrace.first
      javaThreadStateString = nameStateAndStackTrace.second
      rawStackTrace = nameStateAndStackTrace.third

      if (javaThreadStateString == Thread.State.TERMINATED.name) return

      threadName = threadName(nameRaw, threadReference)
      stateString = javaThreadStateToState(javaThreadStateString)

      tid = virtualThreadInfo.second

      isVirtual = true
      isDaemon = false
      prio = null
    }
    else {
      val threadStatus = threadReference.status()
      if (threadStatus == ThreadReference.THREAD_STATUS_ZOMBIE) return

      threadName = threadName(threadReference)
      stateString = threadStatusToState(threadStatus)
      javaThreadStateString = threadStatusToJavaThreadState(threadStatus)

      isVirtual = threadReference is ThreadReferenceImpl && threadReference.isVirtual

      rawStackTrace = getStackTrace(threadReference)

      // Since Project Loom some of Thread's fields are encapsulated into FieldHolder,
      // so we try to look up fields in the thread itself and in its holder.
      val threadType = threadReference.referenceType()
      val (holderObj, holderType) = when (val value = getThreadField("holder", threadType, threadReference, null, null)) {
        is ObjectReference -> value to value.referenceType()
        else -> null to null
      }
      isDaemon = (getThreadField("daemon", threadType, threadReference, holderType, holderObj) as BooleanValue?)?.booleanValue() ?: false
      prio = (getThreadField("priority", threadType, threadReference, holderType, holderObj) as IntegerValue?)?.intValue()
      tid = (getThreadField("tid", threadType, threadReference, holderType, holderObj) as LongValue?)?.longValue()
    }

    val threadState = ThreadState(threadName, stateString)
    threadState.javaThreadState = javaThreadStateString
    nameToThreadMap[threadName] = threadState
    result += threadState

    val buffer = StringBuilder()
    buffer.append('"').append(threadName).append('"')

    if (isDaemon) {
      buffer.append(" daemon")
      threadState.isDaemon = true
    }
    if (prio != null) {
      buffer.append(" prio=").append(prio)
    }
    if (tid != null) {
      buffer.append(" tid=0x").append(JLong.toHexString(tid))
      buffer.append(" nid=NA")
    }
    if (isVirtual) {
      buffer.append(" virtual")
      threadState.isVirtual = true
    }

    buffer.append(" ").append(threadState.state)

    buffer.append("\n  java.lang.Thread.State: ").append(threadState.javaThreadState)

    // There could be too many virtual threads and it's too expensive to collect locking information for all of them.
    val collectMonitorsInfo = virtualThreadInfo == null ||
                              virtualThreads.size < Registry.intValue("debugger.thread.dump.virtual.threads.with.monitors.max.count", 1000)
    try {
      if (collectMonitorsInfo && vmProxy.canGetOwnedMonitorInfo() && vmProxy.canGetMonitorInfo()) {
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

      val waitedMonitor = if (collectMonitorsInfo && vmProxy.canGetCurrentContendedMonitor()) threadReference.currentContendedMonitor() else null
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
      if (collectMonitorsInfo && vmProxy.canGetMonitorFrameInfo()) {
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

  // By default, it includes only platform threads. Unless JDWP's option includevirtualthreads is enabled.
  val threadsFromVM = vmProxy.virtualMachine.allThreads()
  threadsFromVM.forEach {
    processOne(it, null)
  }

  val threadsFromVMSet = threadsFromVM.toSet()
  virtualThreads.forEach { (vthread, stackTrace, tid) ->
    // thread might be already processed if JDWP's option includevirtualthreads is enabled.
    if (vthread !in threadsFromVMSet) {
      processOne(vthread, stackTrace to tid)
    }
  }

  for ((waiting, awaited) in waitingMap) {
    val waitingThread = nameToThreadMap[waiting] ?: continue // continue if zombie
    val awaitedThread = nameToThreadMap[awaited] ?: continue // continue if zombie
    awaitedThread.addWaitingThread(waitingThread)
  }

  // detect simple deadlocks
  for (thread in result) {
    ProgressManager.checkCanceled()
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

private fun getStackTrace(threadReference: ThreadReference): String {
  val frames =
    try {
      threadReference.frames()
    }
    catch (e: IncompatibleThreadStateException) {
      logger<ThreadDumpAction>().error(e)
      return "Incompatible thread state: thread not suspended"
    }

  return buildString {
    for (stackFrame in frames) {
      if (this.isNotEmpty()) {
        append('\n')
      }
      append("\t")
      try {
        append(ThreadDumpAction.renderLocation(stackFrame.location()))
      }
      catch (e: InvalidStackFrameException) {
        logger<ThreadDumpAction>().error(e)
        append("Invalid stack frame: ").append(e.message)
      }
    }
  }
}

private fun splitFirstTwoAndRemainingLines(text: String): Triple<String, String, String> {
  val first = text.lineSequence().first()
  val second = text.lineSequence().drop(1).first()
  val remaining = text.lineSequence().drop(2).joinToString("\n")
  return Triple(first, second, remaining)
}

private class JavaThreadsProvider : ThreadDumpItemsProviderFactory() {
  override fun getProvider(context: DebuggerContextImpl) = object : ThreadDumpItemsProvider {
    val vm = context.debugProcess!!.virtualMachineProxy

    private val shouldDumpVirtualThreads =
      Registry.`is`("debugger.thread.dump.include.virtual.threads") &&
      // Virtual threads first appeared in Java 19 as part of Project Loom.
      JavaVersion.parse(vm.version()).feature >= 19 &&
      // Check if VirtualThread class is at least loaded.
      vm.classesByName("java.lang.VirtualThread").isNotEmpty()

    override val progressText: String
      get() = JavaDebuggerBundle.message(
        if (shouldDumpVirtualThreads) "thread.dump.platform.and.virtual.threads.progress" else "thread.dump.platform.threads.progress"
      )

    override val requiresEvaluation get() = shouldDumpVirtualThreads

    override fun getItems(suspendContext: SuspendContextImpl?): List<MergeableDumpItem> {
      val virtualThreads =
        if (shouldDumpVirtualThreads) evaluateAndGetAllVirtualThreads(suspendContext!!)
        else emptyList()

      return buildThreadStates(vm, virtualThreads).toDumpItems()
    }

    private fun evaluateAndGetAllVirtualThreads(suspendContext: SuspendContextImpl): List<Triple<ThreadReference, String, Long>> {
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
      if (evaluated == null) return emptyList()

      val (packedThreadsAndStackTraces, threadIds) = (evaluated as ArrayReference).values.map { (it as ArrayReference).values }

      ProgressManager.checkCanceled()
      return buildList {
        var tidIdx = 0
        var stIdx = 0
        while (stIdx < packedThreadsAndStackTraces.size) {
          val stackTrace = (packedThreadsAndStackTraces[stIdx++] as StringReference).value()
          while (true) {
            val thread = packedThreadsAndStackTraces[stIdx++]
            if (thread == null) {
              break
            }
            val threadId = (threadIds[tidIdx++] as LongValue).value()
            add(Triple(thread as ThreadReference, stackTrace, threadId))
          }
        }
      }
    }
  }
}