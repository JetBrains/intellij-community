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
import com.intellij.debugger.statistics.DebuggerStatistics
import com.intellij.debugger.statistics.ThreadDumpStatus
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.removeUserData
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.rt.debugger.VirtualThreadDumper
import com.intellij.threadDumpParser.ThreadDumpParser
import com.intellij.threadDumpParser.ThreadState
import com.intellij.unscramble.InfoDumpItem
import com.intellij.unscramble.MergeableDumpItem
import com.intellij.unscramble.toDumpItems
import com.intellij.util.lang.JavaVersion
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.jetbrains.jdi.ThreadReferenceImpl
import com.sun.jdi.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import java.lang.Long as JLong

class ThreadDumpAction {
  companion object {
    private val extendedProviders: ExtensionPointName<ThreadDumpItemsProviderFactory> =
      ExtensionPointName.Companion.create("com.intellij.debugger.dumpItemsProvider")

    private val EVALUATION_IN_PROGRESS = Key.create<Boolean>("intellij.java.debugger.evaluation.in.progress")

    @JvmStatic
    fun buildThreadStates(vmProxy: VirtualMachineProxyImpl): List<ThreadState> {
      val platformThreads = vmProxy.virtualMachine.allThreads()
      return buildThreadStates(vmProxy, platformThreads, virtualThreads = emptyList())
    }

    @JvmStatic
    fun renderLocation(location: Location): @NonNls String {
      return "at " + DebuggerUtilsEx.getLocationMethodQName(location) +
             "(" + DebuggerUtilsEx.getSourceName(location, "Unknown Source") + ":" + DebuggerUtilsEx.getLineNumber(location, false) + ")"
    }

    @ApiStatus.Internal
    suspend fun buildThreadDump(context: DebuggerContextImpl, onlyPlatformThreads: Boolean, dumpItemsChannel: SendChannel<List<MergeableDumpItem>>) {
      suspend fun sendJavaPlatformThreads() {
        val platformThreads = buildJavaPlatformThreadDump(context).toDumpItems()
        dumpItemsChannel.send(platformThreads)
      }

      if (onlyPlatformThreads || !Registry.`is`("debugger.thread.dump.extended")) {
        sendJavaPlatformThreads()
        dumpItemsChannel.send(listOf(InfoDumpItem(
          JavaDebuggerBundle.message("thread.dump.unavailable.title"),
          "Collection of extended dump was disabled.")))
        DebuggerStatistics.logPlatformThreadDumpFallback(
          context.project, if (onlyPlatformThreads) ThreadDumpStatus.PLATFORM_DUMP_ALT_CLICK else ThreadDumpStatus.PLATFORM_DUMP_EXTENDED_DUMP_DISABLED
        )
        return
      }

      try {
        val providers = extendedProviders.extensionList.map { it.getProvider(context) }

        suspend fun sendAllItems(suspendContext: SuspendContextImpl?) {
          coroutineScope {
            sendJavaPlatformThreads()

            for (p in providers) {
              val items = try {
                withBackgroundProgress(context.project,
                                       JavaDebuggerBundle.message("thread.dump.progress.message", p.itemsName)) {
                  p.getItems(suspendContext)
                }
              }
              catch (@Suppress("IncorrectCancellationExceptionHandling") _: CancellationException) {
                thisLogger().debug("Dumping of ${p.itemsName} was cancelled by user.")
                listOf(InfoDumpItem(
                  JavaDebuggerBundle.message("thread.dump.unavailable.title"),
                  "Dumping of ${p.itemsName} was cancelled."))
              }
              dumpItemsChannel.send(items)
            }
          }
        }

        if (providers.any { it.requiresEvaluation }) {

          val vm = context.debugProcess!!.virtualMachineProxy
          // If the previous dump is still being evaluated, only show the Java platform thread dump and do not start a new evaluation.
          if (vm.getUserData(EVALUATION_IN_PROGRESS) == true) {
            sendJavaPlatformThreads()
            dumpItemsChannel.send(listOf(InfoDumpItem(
              JavaDebuggerBundle.message("thread.dump.unavailable.title"),
              "Previous dump is still in progress.")))
            DebuggerStatistics.logPlatformThreadDumpFallback(context.project, ThreadDumpStatus.PLATFORM_DUMP_FALLBACK_DURING_EVALUATION)
            XDebuggerManagerImpl.getNotificationGroup()
              .createNotification(JavaDebuggerBundle.message("thread.dump.during.previous.dump.evaluation.warning"), NotificationType.INFORMATION)
              .notify(context.project)
            return
          }

          val timeout = Registry.intValue("debugger.thread.dump.suspension.timeout.ms", 500).milliseconds
          try {
            vm.putUserData(EVALUATION_IN_PROGRESS, true)
            suspendAllAndEvaluate(context, timeout) { suspendContext ->
              sendAllItems(suspendContext)
            }
          }
          catch (_: TimeoutCancellationException) {
            thisLogger().warn("timeout while waiting for evaluatable context ($timeout)")
            sendJavaPlatformThreads()
            dumpItemsChannel.send(listOf(InfoDumpItem(
              JavaDebuggerBundle.message("thread.dump.unavailable.title"),
              "Timeout while waiting for evaluatable context, unable to dump ${providers.joinToString(", ") { it.itemsName }}.")))
            DebuggerStatistics.logPlatformThreadDumpFallback(context.project, ThreadDumpStatus.PLATFORM_DUMP_FALLBACK_TIMEOUT)
          } finally {
            vm.removeUserData(EVALUATION_IN_PROGRESS)
          }
        }
        else {
          val vm = context.debugProcess!!.virtualMachineProxy
          vm.suspend()
          try {
            sendAllItems(null)
          }
          finally {
            vm.resume()
          }
        }
      }
      catch (e: Throwable) {
        when (e) {
          is CancellationException, is ControlFlowException -> throw e
          else -> {
            thisLogger().error(e)
            // There is no sense to try to send Java platform threads once again.
            dumpItemsChannel.send(listOf(InfoDumpItem(
              JavaDebuggerBundle.message("thread.dump.unavailable.title"),
              "Some internal error happened.")))
            DebuggerStatistics.logPlatformThreadDumpFallback(context.project, ThreadDumpStatus.PLATFORM_DUMP_FALLBACK_ERROR)
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

private inline fun <reified T : Type> findThreadFieldImpl(fieldNames: List<String>, typeToSearch: ReferenceType): Field? {
  val wellNamedFields = fieldNames.mapNotNull { DebuggerUtils.findField(typeToSearch, it) }
  if (wellNamedFields.isEmpty()) return null

  val wellTypedFields = wellNamedFields.filter { it.type() is T }
  if (wellTypedFields.isEmpty()) {
    val vm = typeToSearch.virtualMachine()
    logger<ThreadDumpAction>().error(
      "$typeToSearch has following fields ${wellNamedFields.map { it.name() }} with unexpected types ${wellNamedFields.map { it.type() }}, skipping it. " +
      "VM: ${vm.name()}, ${vm.version()}.")
    return null
  }

  if (wellTypedFields.size > 1) {
    val vm = typeToSearch.virtualMachine()
    logger<ThreadDumpAction>().error(
      "$typeToSearch has ambiguous list of fields ${wellTypedFields.map { it.name() }}, taking the first one. " +
      "VM: ${vm.name()}, ${vm.version()}.")
  }

  return wellTypedFields.first()
}

private inline fun <reified T : Type> findThreadField(fieldNames: List<String>, jlThreadType: ReferenceType?, fieldHolderType: ReferenceType?, optional: Boolean = false): Field? {
  if (jlThreadType == null) return null

  findThreadFieldImpl<T>(fieldNames, jlThreadType)?.let {
    return it
  }

  if (fieldHolderType != null) {
    findThreadFieldImpl<T>(fieldNames, fieldHolderType)?.let {
      return it
    }
  }

  if (!optional) {
    val vm = jlThreadType.virtualMachine()
    logger<ThreadDumpAction>().error(
      if (fieldHolderType != null) {
        "$jlThreadType and $fieldHolderType have "
      } else {
        "$jlThreadType has "
      } +
      "none of fields $fieldNames. " +
      "VM: ${vm.name()}, ${vm.version()}.")
  }

  return null
}

private inline fun <reified T : Type> findThreadField(fieldName: String, jlThreadType: ReferenceType?, fieldHolderType: ReferenceType?, optional: Boolean = false): Field? =
  findThreadField<T>(listOf(fieldName), jlThreadType, fieldHolderType, optional)

private fun buildThreadStates(
  vmProxy: VirtualMachineProxyImpl,
  platformThreads: List<ThreadReference>,
  virtualThreads: List<Triple<ThreadReference, String, Long>>,
): List<ThreadState> {

  val result = mutableListOf<ThreadState>()
  val nameToThreadMap = mutableMapOf<String, ThreadState>()
  val waitingMap = mutableMapOf<String, String>() // key 'waits_for' value

  val jlThreadType = platformThreads.firstOrNull()
    ?.let { someThread ->
      val jlThreadName = "java.lang.Thread"
      val someThreadType = someThread.referenceType()
      generateSequence(someThreadType as? ClassType) { it.superclass() }
        .firstOrNull { it.name() == jlThreadName }
        .also { if (it == null) logger<ThreadDumpAction>().error("$someThreadType is expected to have $jlThreadName as super type") }
    }

  // Since Project Loom some of Thread's fields have been encapsulated into FieldHolder,
  // so we try to look up fields in the thread itself and in its holder.
  val holderField = findThreadField<ClassType>("holder", jlThreadType, null, optional = true)

  val fieldHolderType = holderField?.type()?.let { it as ClassType }
  val daemonField = findThreadField<BooleanType>(listOf("daemon", "isDaemon"), jlThreadType, fieldHolderType)
  val priorityField = findThreadField<IntegerType>("priority", jlThreadType, fieldHolderType)
  val tidField = findThreadField<LongType>("tid", jlThreadType, fieldHolderType)

  fun getFieldValue(field: Field?, threadReference: ThreadReference, fieldHolder: ObjectReference?): Value? {
    if (field == null) return null

    return when (val fieldHost = field.declaringType()) {
      jlThreadType -> threadReference.getValue(field)
      fieldHolderType -> fieldHolder!!.getValue(field)
      else -> { logger<ThreadDumpAction>().error("unexpected declaring type of field $field: $fieldHost"); null }
    }
  }

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

      val holderObj = getFieldValue(holderField, threadReference, null)?.let { it as ObjectReference }
      isDaemon = getFieldValue(daemonField, threadReference, holderObj)?.let { (it as BooleanValue).booleanValue() } ?: false
      prio = getFieldValue(priorityField, threadReference, holderObj)?.let { (it as IntegerValue).intValue() }
      tid = getFieldValue(tidField, threadReference, holderObj)?.let { (it as LongValue).longValue() }
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

  // For the sake of better UX (i.e., showing platform threads immediately and only then evaluating extended dump)
  // we process platform and virtual threads independently.
  // However, there might be duplicates (rare case, when JDWP's option includevirtualthreads is enabled)
  // and there might be broken awaiting threads links between platform and virtual threads (not so important feature, might be ignored).

  platformThreads.forEach { pthread ->
    processOne(pthread, virtualThreadInfo = null)
  }

  virtualThreads.forEach { (vthread, stackTrace, tid) ->
    processOne(vthread, stackTrace to tid)
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

private class JavaVirtualThreadsProvider : ThreadDumpItemsProviderFactory() {
  override fun getProvider(context: DebuggerContextImpl) = object : ThreadDumpItemsProvider {
    val vm = context.debugProcess!!.virtualMachineProxy

    private val enabled =
      Registry.`is`("debugger.thread.dump.include.virtual.threads") &&
      // Virtual threads first appeared in Java 19 as part of Project Loom.
      JavaVersion.parse(vm.version()).feature >= 19 &&
      // Check if VirtualThread class is at least loaded.
      vm.classesByName("java.lang.VirtualThread").isNotEmpty()

    override val itemsName: String
      get() = JavaDebuggerBundle.message("thread.dump.virtual.threads.name")

    override val requiresEvaluation get() = enabled

    override fun getItems(suspendContext: SuspendContextImpl?): List<MergeableDumpItem> {
      return (
        if (!enabled) emptyList()
        else {
          val virtualThreads = evaluateAndGetAllVirtualThreads(suspendContext!!)
          buildThreadStates(vm, platformThreads = emptyList(), virtualThreads).toDumpItems()
        })
        .also { DebuggerStatistics.logVirtualThreadsDump(context.project, it.size) }
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