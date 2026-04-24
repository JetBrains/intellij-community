// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.MethodInvokeUtils.getMethodHandlesImplLookup
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.suspendAllAndEvaluate
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.impl.ThreadDumpItemsProvider
import com.intellij.debugger.impl.ThreadDumpItemsProviderFactory
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
import com.intellij.unscramble.JavaThreadContainerDesc
import com.intellij.unscramble.MergeableDumpItem
import com.intellij.unscramble.toDumpItems
import com.intellij.util.lang.JavaVersion
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.jetbrains.jdi.ThreadReferenceImpl
import com.sun.jdi.ArrayReference
import com.sun.jdi.BooleanType
import com.sun.jdi.BooleanValue
import com.sun.jdi.ClassNotLoadedException
import com.sun.jdi.ClassType
import com.sun.jdi.Field
import com.sun.jdi.IncompatibleThreadStateException
import com.sun.jdi.IntegerType
import com.sun.jdi.IntegerValue
import com.sun.jdi.InvalidStackFrameException
import com.sun.jdi.Location
import com.sun.jdi.LongType
import com.sun.jdi.LongValue
import com.sun.jdi.MonitorInfo
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.StringReference
import com.sun.jdi.ThreadReference
import com.sun.jdi.Type
import com.sun.jdi.Value
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CancellationException
import kotlin.collections.set
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
      return buildThreadStates(vmProxy, platformThreads, virtualThreads = emptyList(), emptyList(), null)
    }

    @JvmStatic
    fun renderLocation(location: Location): @NonNls String {
      return "at " + DebuggerUtilsEx.getLocationMethodQName(location) +
             "(" + DebuggerUtilsEx.getSourceName(location, "Unknown Source") + ":" + DebuggerUtilsEx.getLineNumber(location, false) + ")"
    }

    @ApiStatus.Internal
    suspend fun buildThreadDump(context: DebuggerContextImpl, onlyPlatformThreads: Boolean, dumpItemsChannel: SendChannel<List<MergeableDumpItem>>) {
      suspend fun sendJavaPlatformThreads() {
        val platformThreads = toDumpItems(buildJavaPlatformThreadDump())
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

          val vm = VirtualMachineProxyImpl.getCurrent()
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
          val vm = VirtualMachineProxyImpl.getCurrent()
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

    fun buildJavaPlatformThreadDump(): List<ThreadState> {
      val vm = VirtualMachineProxyImpl.getCurrent()
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

  val wellTypedFields = try {
     wellNamedFields.filter { it.type() is T }
  } catch (_: ClassNotLoadedException) {
    logger<ThreadDumpAction>().info(
      "$typeToSearch has the fields ${wellNamedFields.map { it.name() }} whose type is not yet loaded, skipping. " +
      "VM: ${typeToSearch.virtualMachine().let { "${it.name()}, ${it.version()}" }}"
    )
    return null
  }

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

private data class JavaVirtualThreadDesc(
  val thread: ThreadReference,
  val stackTrace: String,
  val threadId: Long,
  val carrierId: Long?
)

private inline fun <reified T : Type> findThreadField(fieldName: String, jlThreadType: ReferenceType?, fieldHolderType: ReferenceType?, optional: Boolean = false): Field? =
  findThreadField<T>(listOf(fieldName), jlThreadType, fieldHolderType, optional)

private fun buildThreadStates(
  vmProxy: VirtualMachineProxyImpl,
  platformThreads: List<ThreadReference>,
  virtualThreads: List<JavaVirtualThreadDesc>,
  threadContainerRefs: List<ObjectReference>,
  rootThreadContainer: ObjectReference?
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
  val containerField = findThreadField<ClassType>("container", jlThreadType, null, optional = true)

  fun getFieldValue(field: Field?, threadReference: ThreadReference, fieldHolder: ObjectReference?): Value? {
    if (field == null) return null

    return when (val fieldHost = field.declaringType()) {
      jlThreadType -> threadReference.getValue(field)
      fieldHolderType -> fieldHolder!!.getValue(field)
      else -> { logger<ThreadDumpAction>().error("unexpected declaring type of field $field: $fieldHost"); null }
    }
  }

  fun processOne(threadReference: ThreadReference, virtualThreadInfo: JavaVirtualThreadDesc?) {
    ProgressManager.checkCanceled()

    val threadName: String
    val stateString: String
    val javaThreadStateString: String
    val threadContainerId: Long?
    val isVirtual: Boolean
    val isDaemon: Boolean
    val tid: Long?
    val carrierId: Long?
    val prio: Int?
    val rawStackTrace: String
    if (virtualThreadInfo != null) {
      val lines = virtualThreadInfo.stackTrace.lineSequence()
      val (nameRaw, javaThreadState, threadContainerIdx) = lines.take(3).toList()
      rawStackTrace = lines.drop(3).joinToString("\n")

      if (javaThreadState == Thread.State.TERMINATED.name) return

      threadName = threadName(nameRaw, threadReference)
      stateString = javaThreadStateToState(javaThreadState)
      javaThreadStateString = javaThreadState
      threadContainerId = containerIdOrNullIfRoot(threadContainerRefs[threadContainerIdx.toInt()], rootThreadContainer)

      tid = virtualThreadInfo.threadId
      carrierId = virtualThreadInfo.carrierId
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
      val container = getFieldValue(containerField, threadReference, null)?.let { it as? ObjectReference }
      threadContainerId = containerIdOrNullIfRoot(container, rootThreadContainer)
      carrierId = null
    }
    val threadState = ThreadState(threadName, stateString)
    threadState.javaThreadState = javaThreadStateString
    threadState.uniqueId = threadReference.uniqueID()
    threadState.threadContainerUniqueId = threadContainerId

    nameToThreadMap[threadName] = threadState
    result += threadState

    val buffer = StringBuilder()
    threadState.isDaemon = isDaemon
    threadState.isVirtual = isVirtual

    // TODO: extract header creation to a function, which can be called from thread dump parsers, see JcmdJsonThreadDumpParser
    buffer.append(threadState.createHeader(threadName, prio, tid, carrierId))

    // There could be too many virtual threads and it's too expensive to collect locking information for all of them.
    val collectMonitorsInfo = virtualThreadInfo == null ||
                              virtualThreads.size < Registry.intValue("debugger.thread.dump.virtual.threads.with.monitors.max.count", 1000)
    try {
      // 1. Collect info about owned monitors (including stack depth at which this monitor was acquired by the owning thread if possible)
      if (collectMonitorsInfo && vmProxy.canGetOwnedMonitorInfo() && vmProxy.canGetMonitorInfo()) {
        if (vmProxy.canGetMonitorFrameInfo()) {
          for (m in threadReference.ownedMonitorsAndFrames()) {
            if (m is MonitorInfo) { // see JRE-937
              threadState.addOwnedMonitorAtDepth(renderObject(m.monitor()), m.stackDepth())
            }
          }
        }
        else {  // java 5 and earlier, no frames info
          for (ownedMonitor in threadReference.ownedMonitors()) {
            threadState.addOwnedMonitor(renderObject(ownedMonitor))
          }
        }
      }

      // 2. Set contended monitor
      val contendedMonitor =
        if (collectMonitorsInfo && vmProxy.canGetCurrentContendedMonitor()) threadReference.currentContendedMonitor() else null
      threadState.contendedMonitor = contendedMonitor?.let { renderObject(it) }
    }
    catch (_: IncompatibleThreadStateException) {
      buffer.append("\n\t Incompatible thread state: thread not suspended")
    }

    buffer.append('\n').append(rawStackTrace)
    val hasEmptyStack = rawStackTrace.isEmpty()
    threadState.setStackTrace(buffer.toString(), hasEmptyStack)
  }

  // For the sake of better UX (i.e., showing platform threads immediately and only then evaluating extended dump)
  // we process platform and virtual threads independently.
  // However, there might be duplicates (rare case, when JDWP's option includevirtualthreads is enabled)
  // and there might be broken awaiting threads links between platform and virtual threads (not so important feature, might be ignored).

  platformThreads.forEach { pthread ->
    processOne(pthread, virtualThreadInfo = null)
  }

  if (virtualThreads.isNotEmpty()) {
    require(threadContainerRefs.isNotEmpty()) { "The list of thread container references was not provided for virtual threads." }
  }
  virtualThreads.forEach {
    processOne(it.thread, it)
  }

  for (threadState in result) {
    ThreadDumpParser.inferThreadStateDetail(threadState)
  }

  ThreadDumpParser.enrichStackTraceWithLockInfo(result)

  ThreadDumpParser.detectWaitingAndDeadlockedThreads(result)

  ThreadDumpParser.sortThreads(result)
  return result
}

private fun Long.toThreadIdString(): String = "0x" + JLong.toHexString(this)

private fun ThreadState.createHeader(
  threadName: String,
  prio: Int?,
  tid: Long?,
  carrierId: Long?
): String {
  val buffer = StringBuilder()
  buffer.append('"').append(threadName).append('"')

  if (isDaemon) {
    buffer.append(" daemon")
  }
  if (prio != null) {
    buffer.append(" prio=").append(prio)
  }
  if (tid != null) {
    buffer.append(" tid=").append(tid.toThreadIdString())
    buffer.append(" nid=NA")
  }
  if (isVirtual) {
    buffer.append(" virtual")

    if (carrierId != null) {
      buffer.append(" carrierId=${carrierId.toThreadIdString()}")
    } else {
      buffer.append(" unmounted")
    }
  }

  buffer.append(" ").append(state)

  buffer.append("\n  java.lang.Thread.State: ").append(javaThreadState)
  return buffer.toString()
}

private fun getRootThreadContainer(vm: VirtualMachineProxyImpl): ObjectReference? {
  val type = vm.classesByName("jdk.internal.vm.ThreadContainers").firstOrNull() as? ClassType
             ?: return null // e.g., in case of pre-Loom Java
  val field = DebuggerUtils.findField(type, "ROOT_CONTAINER") ?: run {
    logger<ThreadDumpAction>().error(
      "ThreadContainers class has no field ROOT_CONTAINER. " +
      "VM: ${vm.name()}, ${vm.version()}.")
    return null
  }
  return type.getValue(field) as? ObjectReference ?: run {
    logger<ThreadDumpAction>().error(
      "ThreadContainers class has an unexpected value of the field ROOT_CONTAINER. " +
      "VM: ${vm.name()}, ${vm.version()}.")
    return null
  }
}

/**
 * Returns the unique ID of the thread container [containerRef], or `null` if it is the root container.
 *
 * The root container [jdk.internal.vm.ThreadContainers.RootContainer] is a default top-level container, it's not created by user, so it can be omitted from the UI hierarchy.
 */
private fun containerIdOrNullIfRoot(containerRef: ObjectReference?, rootContainer: ObjectReference?): Long? =
  if (containerRef == rootContainer) null else containerRef?.uniqueID()

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

internal class JavaVirtualThreadsProvider : ThreadDumpItemsProviderFactory() {
  override fun getProvider(context: DebuggerContextImpl) = object : ThreadDumpItemsProvider {
    val vm = VirtualMachineProxyImpl.getCurrent()

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
          evaluateAndGetAllVirtualThreadsDumpItems(suspendContext!!)
        })
        .also { DebuggerStatistics.logVirtualThreadsDump(context.project, it.size) }
    }

    private fun evaluateAndGetAllVirtualThreadsDumpItems(suspendContext: SuspendContextImpl): List<MergeableDumpItem> {
      val evaluationContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)

      val lookupImpl = getMethodHandlesImplLookup(evaluationContext)
      if (lookupImpl == null) {
        throw EvaluateException("Cannot get MethodHandles.Lookup.IMPL_LOOKUP")
      }

      val evaluated =
        DebuggerUtilsImpl.invokeHelperMethod(
          evaluationContext,
          VirtualThreadDumper::class.java, "getAllVirtualThreadsWithStackTracesAndContainers",
          listOf(lookupImpl)
        )

      val packedThreadsAndStackTraces = ((evaluated as ArrayReference).values[0] as ArrayReference).values
      val threadIds = (evaluated.values[1] as ArrayReference).values
      val carrierIds = (evaluated.values[2] as ArrayReference).values
      val threadContainerNames = (evaluated.values[3] as ArrayReference).values.map { (it as StringReference).value() }
      val threadContainerRefs = (evaluated.values[4] as ArrayReference).values.map { it as ObjectReference }
      val threadContainerOwners = (evaluated.values[5] as ArrayReference).values.map { it as? ObjectReference }
      val parentContainerOrdinals = (evaluated.values[6] as ArrayReference).values.map { (it as IntegerValue).intValue() }

      require(threadIds.size == carrierIds.size) { "The number of thread IDs should be equal the number of carrier thread IDs." }
      require(threadContainerNames.size == threadContainerRefs.size) { "The number of thread container names should be equal the number of thread container references." }
      require(threadContainerNames.size == threadContainerOwners.size) { "The number of thread container names should be equal the number of thread container owners." }
      require(threadContainerNames.size == parentContainerOrdinals.size) { "The number of thread container names should be equal the number of corresponding parent container ordinals." }

      val rootContainer = getRootThreadContainer(vm)
      val threadStates = buildVirtualThreadStates(packedThreadsAndStackTraces, threadIds, carrierIds, threadContainerRefs, rootContainer)

      val threadContainerDescriptors = threadContainerNames.indices.map { i ->
        val parentOrdinal = parentContainerOrdinals[i]
        val parentContainerRef = if (parentOrdinal == -1) null else threadContainerRefs[parentOrdinal]
        val ownerThread = threadContainerOwners[i]
        // If owner thread of the thread container is null, then use its parent container to display the hierarchy.
        val parentId = ownerThread?.uniqueID() ?: containerIdOrNullIfRoot(parentContainerRef, rootContainer)
        JavaThreadContainerDesc(threadContainerNames[i], threadContainerRefs[i].uniqueID(), parentId)
      }
      return toDumpItems(threadStates, threadContainerDescriptors)
    }

    private fun buildVirtualThreadStates(packedThreadsAndStackTraces: List<Value?>, threadIds: List<Value?>, carrierIds: List<Value?>, threadContainerRefs: List<ObjectReference>, rootContainer: ObjectReference?): List<ThreadState> {
      ProgressManager.checkCanceled()
      val virtualThreads = buildList {
        var tidIdx = 0
        var stIdx = 0
        while (stIdx < packedThreadsAndStackTraces.size) {
          val stackTrace = (packedThreadsAndStackTraces[stIdx++] as StringReference).value()
          while (true) {
            val thread = packedThreadsAndStackTraces[stIdx++]
            if (thread == null) {
              break
            }
            val threadId = (threadIds[tidIdx] as LongValue).value()
            val carrierId = (carrierIds[tidIdx++] as LongValue).value().let { if (it == -1L) null else it }
            add(JavaVirtualThreadDesc(thread as ThreadReference, stackTrace, threadId, carrierId))
          }
        }
      }
      return buildThreadStates(vm, platformThreads = emptyList(), virtualThreads, threadContainerRefs, rootContainer)
    }
  }
}
